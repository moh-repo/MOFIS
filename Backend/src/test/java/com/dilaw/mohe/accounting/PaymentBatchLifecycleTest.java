package com.dilaw.mohe.accounting;

import com.dilaw.mohe.accounting.dto.PaymentBatchCreateRequest;
import com.dilaw.mohe.accounting.dto.PaymentBatchDetail;
import com.dilaw.mohe.accounting.dto.PaymentBatchSummary;
import com.dilaw.mohe.accounting.model.PaymentBatch;
import com.dilaw.mohe.accounting.service.PaymentBatchService;
import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A payment batch from preparation to payment, against the live database.
 *
 * <p>One scenario, end to end: the ministry prepares a batch for Egypt in the
 * latest quarter, Jordan cannot see it, the preparer cannot approve it, another
 * administrator does, and paying it moves the attaché's advance by exactly the
 * disbursed total — not the entitled one.
 *
 * <p>Runs as real UMS accounts through {@link AsUser}, and sets the tenant scope
 * alongside: {@code TenantContext} is what the data source stamps into
 * {@code SESSION_CONTEXT}, and with {@code tenant_country_policy} enabled a
 * thread that has not set it reads nothing at all.
 *
 * <p>Self-cleaning: every row this test inserts is deleted in {@link #cleanUp()},
 * and the advance balance it borrows is put back to the figure it had.
 */
@Tag("integration")
@SpringBootTest
class PaymentBatchLifecycleTest {

    /** GBP: Egypt holds no GBP advance for the latest year, so the balance is this test's own. */
    private static final int CURRENCY = 2;
    private static final int EGYPT = 1;
    private static final BigDecimal BORROWED = new BigDecimal("5000.00");

    @Autowired private PaymentBatchService batchService;
    @Autowired private JdbcTemplate jdbc;

    private final List<Integer> createdBatchIds = new ArrayList<>();
    private Integer fiscalYearId;
    private boolean balanceInserted;
    private BigDecimal balanceDisbursedBefore;

    @AfterEach
    void cleanUp() {
        AsUser.signOut();
        TenantContext.runAsSystem(() -> {
            for (Integer id : createdBatchIds) {
                jdbc.update("DELETE FROM FINCORE.PAYMENT_BATCH_LINE WHERE BATCH_ID = ?", id);
                jdbc.update("DELETE FROM FINCORE.PAYMENT_BATCH WHERE BATCH_ID = ?", id);
            }
            if (fiscalYearId != null) {
                if (balanceInserted) {
                    jdbc.update("""
                            DELETE FROM FINCORE.ATTACHE_ADVANCE_BALANCE
                            WHERE FISCAL_YEAR_ID = ? AND STUDY_COUNTRY_IN_ID = ? AND CURRENCY_ID = ?
                            """, fiscalYearId, EGYPT, CURRENCY);
                } else if (balanceDisbursedBefore != null) {
                    jdbc.update("""
                            UPDATE FINCORE.ATTACHE_ADVANCE_BALANCE
                            SET TRANSFERRED = TRANSFERRED - ?, DISBURSED = ?
                            WHERE FISCAL_YEAR_ID = ? AND STUDY_COUNTRY_IN_ID = ? AND CURRENCY_ID = ?
                            """, BORROWED, balanceDisbursedBefore, fiscalYearId, EGYPT, CURRENCY);
                }
            }
        });
    }

    @Test
    @DisplayName("prepare → refuse the preparer → approve → pay, scoped to the attaché throughout")
    void lifecycle() {
        Integer quarter = system(() -> jdbc.queryForObject(
                "SELECT MAX(QUARTER) FROM MOHECORE.MOHE_QUARTER", Integer.class));
        assumeTrue(quarter != null, "no MOHE quarters");
        fiscalYearId = system(() -> jdbc.queryForObject(
                "SELECT FISCAL_YEAR_ID FROM FINCORE.FISCAL_YEAR WHERE YEAR_NO = ?", Integer.class, quarter / 10));
        assumeTrue(fiscalYearId != null, "no fiscal year for quarter " + quarter);

        List<Integer> students = system(() -> jdbc.queryForList(
                "SELECT TOP 2 STUDENT_ID FROM MOHECORE.STUDENT_DATA WHERE STUDY_COUNTRY_IN = ? ORDER BY STUDENT_ID",
                Integer.class, EGYPT));
        assumeTrue(students.size() == 2, "need two Egyptian students");

        lendEgyptSomeCash();

        // ── prepare, as the ministry ──────────────────────────────────────────
        PaymentBatchCreateRequest request = new PaymentBatchCreateRequest(quarter, EGYPT, 1, CURRENCY,
                "PaymentBatchLifecycleTest",
                List.of(new PaymentBatchCreateRequest.Line(students.get(0), new BigDecimal("300"), null, null),
                        new PaymentBatchCreateRequest.Line(students.get(1), new BigDecimal("200"),
                                new BigDecimal("150"), "مستندات ناقصة / documents missing")));

        PaymentBatchDetail created = as(AsUser.SUPERADMIN, TenantContext.Scope.SYSTEM,
                () -> batchService.create(request));
        createdBatchIds.add(created.batch().id());

        assertThat(created.batch().status()).isEqualTo(PaymentBatch.OPEN);
        assertThat(created.batch().preparedBy()).isEqualTo((int) AsUser.SUPERADMIN);
        assertThat(created.batch().fiscalYearId()).isEqualTo(fiscalYearId);
        assertThat(created.batch().entitledTotal()).isEqualByComparingTo("500");
        assertThat(created.batch().disbursedTotal()).isEqualByComparingTo("450");
        assertThat(created.lines()).hasSize(2)
                .allSatisfy(l -> assertThat(l.studentName()).as("names resolved for the dialog").isNotBlank());

        // ── scope: Jordan sees nothing of it ──────────────────────────────────
        TenantContext.Scope jordan = TenantContext.Scope.of(false, List.of(1004));
        List<PaymentBatchSummary> jordanSees = as(AsUser.JO_ADMIN, jordan,
                () -> batchService.forQuarter(quarter, null));
        assertThat(jordanSees).extracting(PaymentBatchSummary::id)
                .as("how many batches Cairo raised is Cairo's business")
                .doesNotContain(created.batch().id());
        assertThatThrownBy(() -> as(AsUser.JO_ADMIN, jordan, () -> batchService.detail(created.batch().id())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        assertThat(as(AsUser.SUPERADMIN, TenantContext.Scope.SYSTEM, () -> batchService.forQuarter(quarter, EGYPT)))
                .extracting(PaymentBatchSummary::id)
                .as("positive control: the ministry lists it")
                .contains(created.batch().id());

        // ── maker ≠ checker ───────────────────────────────────────────────────
        assertThatThrownBy(() -> as(AsUser.SUPERADMIN, TenantContext.Scope.SYSTEM,
                () -> batchService.approve(created.batch().id(), (int) AsUser.SUPERADMIN)))
                .as("superadmin prepared it, so superadmin may not release it")
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        PaymentBatch approved = as(AsUser.ADMIN, TenantContext.Scope.SYSTEM,
                () -> batchService.approve(created.batch().id(), (int) AsUser.ADMIN));
        assertThat(approved.getStatus()).isEqualTo(PaymentBatch.APPROVED);
        assertThat(approved.getApprovedBy()).isEqualTo((int) AsUser.ADMIN);

        // ── pay: the advance moves by the disbursed total ─────────────────────
        PaymentBatch paid = as(AsUser.ADMIN, TenantContext.Scope.SYSTEM,
                () -> batchService.markPaid(created.batch().id(), (int) AsUser.ADMIN));
        assertThat(paid.getStatus()).isEqualTo(PaymentBatch.DISBURSED);
        assertThat(paid.getDisbursedAt()).isNotNull();

        BigDecimal disbursedNow = system(() -> jdbc.queryForObject("""
                SELECT DISBURSED FROM FINCORE.ATTACHE_ADVANCE_BALANCE
                WHERE FISCAL_YEAR_ID = ? AND STUDY_COUNTRY_IN_ID = ? AND CURRENCY_ID = ?
                """, BigDecimal.class, fiscalYearId, EGYPT, CURRENCY));
        assertThat(disbursedNow.subtract(balanceDisbursedBefore))
                .as("450 left the attaché; the 50 held back is still in the advance")
                .isEqualByComparingTo("450");

        // ── and it cannot be paid twice ───────────────────────────────────────
        assertThatThrownBy(() -> as(AsUser.ADMIN, TenantContext.Scope.SYSTEM,
                () -> batchService.markPaid(created.batch().id(), (int) AsUser.ADMIN)))
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * Gives Egypt a GBP advance to pay from, or tops up the one it has.
     *
     * <p>AVAILABLE is zero on every real balance row (every riyal transferred is
     * either disbursed or held as Wafar), so a batch paid against one would be
     * refused by {@code CK_ADV_NOT_OVERDRAWN} — correctly. Which is why the test
     * lends the attaché the cash first, and takes it back in {@link #cleanUp()}.
     */
    private void lendEgyptSomeCash() {
        List<BigDecimal> existing = system(() -> jdbc.queryForList("""
                SELECT DISBURSED FROM FINCORE.ATTACHE_ADVANCE_BALANCE
                WHERE FISCAL_YEAR_ID = ? AND STUDY_COUNTRY_IN_ID = ? AND CURRENCY_ID = ?
                """, BigDecimal.class, fiscalYearId, EGYPT, CURRENCY));
        if (existing.isEmpty()) {
            system(() -> jdbc.update("""
                    INSERT INTO FINCORE.ATTACHE_ADVANCE_BALANCE
                        (FISCAL_YEAR_ID, STUDY_COUNTRY_IN_ID, CURRENCY_ID, TRANSFERRED)
                    VALUES (?, ?, ?, ?)
                    """, fiscalYearId, EGYPT, CURRENCY, BORROWED));
            balanceInserted = true;
            balanceDisbursedBefore = BigDecimal.ZERO;
        } else {
            balanceDisbursedBefore = existing.get(0);
            system(() -> jdbc.update("""
                    UPDATE FINCORE.ATTACHE_ADVANCE_BALANCE SET TRANSFERRED = TRANSFERRED + ?
                    WHERE FISCAL_YEAR_ID = ? AND STUDY_COUNTRY_IN_ID = ? AND CURRENCY_ID = ?
                    """, BORROWED, fiscalYearId, EGYPT, CURRENCY));
        }
    }

    private static <T> T as(long userId, TenantContext.Scope scope, Supplier<T> body) {
        return TenantContext.runAs(scope, () -> AsUser.call(userId, body));
    }

    private static <T> T system(Supplier<T> body) {
        return TenantContext.runAsSystem(body);
    }
}
