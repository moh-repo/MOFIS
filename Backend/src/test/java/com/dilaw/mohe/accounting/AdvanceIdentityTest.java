package com.dilaw.mohe.accounting;

import com.dilaw.mohe.accounting.model.AttacheAdvanceBalance;
import com.dilaw.mohe.accounting.service.AdvanceBalanceService;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The control identity on a cultural attaché's advance (العهدة).
 *
 * <pre>
 *   AVAILABLE = OPENING + TRANSFERRED - DISBURSED - RETURNED
 *               - (WAFAR_EARMARKED - WAFAR_SPENT)
 * </pre>
 *
 * <p>These tests exist because the pre-existing {@code WafarBalanceService}
 * returns a single global figure — {@code sum(deposits) - sum(spending)} — with
 * no link to the transfer that funded it, no country and no currency. That
 * number implies Cairo's savings can pay Riyadh's students, and sums {@code int}
 * USD, GBP and SAR into one total.
 *
 * <p>Rolled back: it writes real balances against a real fiscal year.
 *
 * <p>Signed in as a ministry user throughout. {@code AdvanceBalanceService} reads
 * the country scope from the security context, and an absent context is
 * {@code noAccess} — correct for an untokened request, and not what these tests
 * are about: the subject here is whether the arithmetic balances, not who may see
 * it. {@code AccountingPagesScopeTest} covers the access question.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class AdvanceIdentityTest {

    /** Egypt — where all 941 students in this database study. */
    private static final int EGYPT = 1;
    /** A country with no students here, so it can stand in for a second attaché. */
    private static final int AUSTRALIA = 1000;
    private static final int USD = 1;

    @Autowired private AdvanceBalanceService advances;
    @Autowired private JdbcTemplate jdbc;

    private Integer fiscalYear;

    @AfterEach
    void signOut() {
        AsUser.signOut();
    }

    @BeforeEach
    void findFiscalYear() {
        AsUser.signIn(AsUser.SUPERADMIN);
        fiscalYear = jdbc.queryForObject(
                "SELECT FISCAL_YEAR_ID FROM FINCORE.FISCAL_YEAR WHERE YEAR_NO = 2026", Integer.class);
        assertThat(fiscalYear).as("V040 seeds fiscal year 2026").isNotNull();
    }

    @Test
    @DisplayName("the identity holds across a transfer, a disbursement, an earmark and a Wafar reuse")
    void identityHolds_acrossTheWholeCycle() {
        advances.openBalance(fiscalYear, EGYPT, USD);

        advances.recordTransfer(fiscalYear, EGYPT, USD, new BigDecimal("100000.00"));   // regular
        advances.recordTransfer(fiscalYear, EGYPT, USD, new BigDecimal("20000.00"));    // additional
        advances.recordDisbursement(fiscalYear, EGYPT, USD, new BigDecimal("3000.00")); // batches 1 and 2

        AttacheAdvanceBalance afterPaying = advances.reload(advances.require(fiscalYear, EGYPT, USD));
        assertThat(afterPaying.getAvailable())
                .as("120000 transferred less 3000 paid out")
                .isEqualByComparingTo("117000.00");

        // A student is ineligible: their entitlement becomes Wafar. No cash moves.
        advances.recordEarmark(fiscalYear, EGYPT, USD, new BigDecimal("1000.00"));

        AttacheAdvanceBalance afterEarmark = advances.reload(advances.require(fiscalYear, EGYPT, USD));
        assertThat(afterEarmark.getWafarBalance()).isEqualByComparingTo("1000.00");
        assertThat(afterEarmark.getAvailable())
                .as("an earmark moves no money, but it does spoken-for 1000 of the cash held")
                .isEqualByComparingTo("116000.00");

        // Spend 600 of that Wafar on another student, with permission.
        advances.recordWafarUse(fiscalYear, EGYPT, USD, new BigDecimal("600.00"));

        AttacheAdvanceBalance afterUse = advances.reload(advances.require(fiscalYear, EGYPT, USD));
        assertThat(afterUse.getAvailable())
                .as("""
                    spending Wafar must leave AVAILABLE untouched: the earmark is released \
                    and the cash goes out at the same moment. Move only one of the two \
                    counters and the balance drifts by the value of every Wafar payment made.""")
                .isEqualByComparingTo("116000.00");
        assertThat(afterUse.getWafarBalance()).isEqualByComparingTo("400.00");
        assertThat(afterUse.getDisbursed())
                .as("the cash did leave, so total disbursed rises to 3000 + 600")
                .isEqualByComparingTo("3600.00");
    }

    @Test
    @DisplayName("an earmark is neither income nor an expense — it only re-earmarks cash already held")
    void wafarIsNotIncome() {
        advances.openBalance(fiscalYear, EGYPT, USD);
        advances.recordTransfer(fiscalYear, EGYPT, USD, new BigDecimal("5000.00"));

        AttacheAdvanceBalance before = advances.reload(advances.require(fiscalYear, EGYPT, USD));
        BigDecimal cashBefore = before.getOpeningBalance()
                .add(before.getTransferred())
                .subtract(before.getDisbursed())
                .subtract(before.getReturned());

        advances.recordEarmark(fiscalYear, EGYPT, USD, new BigDecimal("750.00"));

        AttacheAdvanceBalance after = advances.reload(advances.require(fiscalYear, EGYPT, USD));
        BigDecimal cashAfter = after.getOpeningBalance()
                .add(after.getTransferred())
                .subtract(after.getDisbursed())
                .subtract(after.getReturned());

        assertThat(cashAfter)
                .as("""
                    Wafar is budget already transferred that was not paid out. If an earmark \
                    changed cash, it would be either income or an expense — and booking it as \
                    either double-counts the budget.""")
                .isEqualByComparingTo(cashBefore);
        assertThat(after.getTransferred())
                .as("an earmark must never look like new funding")
                .isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("an attaché cannot pay out money it never held")
    void advanceCannotBeOverdrawn() {
        advances.openBalance(fiscalYear, EGYPT, USD);
        advances.recordTransfer(fiscalYear, EGYPT, USD, new BigDecimal("1000.00"));

        assertThatThrownBy(() ->
                advances.recordDisbursement(fiscalYear, EGYPT, USD, new BigDecimal("5000.00")))
                .as("CK_ADV_NOT_OVERDRAWN must refuse, and it must surface as a 409 rather than a 500")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("overdraw");
    }

    @Test
    @DisplayName("more Wafar cannot be spent than was ever set aside")
    void wafarCannotBeOverspent() {
        advances.openBalance(fiscalYear, EGYPT, USD);
        advances.recordTransfer(fiscalYear, EGYPT, USD, new BigDecimal("10000.00"));
        advances.recordEarmark(fiscalYear, EGYPT, USD, new BigDecimal("400.00"));

        assertThatThrownBy(() ->
                advances.recordWafarUse(fiscalYear, EGYPT, USD, new BigDecimal("401.00")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("exceeds the savings held");
    }

    @Test
    @DisplayName("each attaché has its own pool — Cairo's savings are not Riyadh's")
    void poolsAreSeparatePerAttache() {
        advances.openBalance(fiscalYear, EGYPT, USD);
        advances.recordTransfer(fiscalYear, EGYPT, USD, new BigDecimal("10000.00"));
        advances.recordEarmark(fiscalYear, EGYPT, USD, new BigDecimal("5000.00"));

        advances.openBalance(fiscalYear, AUSTRALIA, USD);

        AttacheAdvanceBalance other = advances.reload(advances.require(fiscalYear, AUSTRALIA, USD));
        assertThat(other.getWafarBalance())
                .as("""
                    Egypt's 5000 of savings must not appear at another attaché. The old \
                    WafarBalanceService returned one global figure with no country at all, \
                    which is exactly the confusion this separation removes.""")
                .isEqualByComparingTo("0.00");

        assertThatThrownBy(() ->
                advances.recordWafarUse(fiscalYear, AUSTRALIA, USD, new BigDecimal("100.00")))
                .as("and the other attaché cannot draw on savings it does not hold")
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("stored totals are reconciled against the rows that produced them")
    void reconciliationDetectsDrift() {
        advances.openBalance(fiscalYear, EGYPT, USD);
        // Written straight to the balance with no allocation row behind it, which
        // is precisely the drift the reconciliation exists to catch: a running
        // total maintained by the service that its own detail cannot justify.
        advances.recordTransfer(fiscalYear, EGYPT, USD, new BigDecimal("777.00"));

        assertThat(advances.reconcile(fiscalYear))
                .as("a transfer with no allocation behind it must be reported, not silently trusted")
                .anySatisfy(d -> {
                    assertThat(d.countryId()).isEqualTo(EGYPT);
                    assertThat(d.transferredDelta()).isEqualByComparingTo("777.00");
                });
    }
}
