package com.dilaw.mohe.accounting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards the dataset V045 derives from the operational tables.
 *
 * <p>V045 reconstructs the fund chain from student finance, university fees,
 * travel tickets, book allowances and Wafar records. Some of it is a genuine
 * transcription; the allocations are an <em>inference</em> — there is no
 * transfer table anywhere in the database, so the amount that reached each
 * attaché is deduced from what was disbursed plus what became Wafar.
 *
 * <p>An inference that does not add up is worse than no data at all, because it
 * looks like a figure. These tests check that it adds up, and that deriving the
 * data did not quietly put codes into the chart of accounts.
 *
 * <p>Read-only, so no transaction and nothing to clean up. Every test skips
 * rather than fails when V045 has not been run — the schema is useful without
 * it, and a CI database with no data should not report a false failure.
 */
@Tag("integration")
@SpringBootTest
class DerivedAccountingDataTest {

    @Autowired private JdbcTemplate jdbc;

    private boolean derivationHasRun() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM FINCORE.ATTACHE_ALLOCATION WHERE TRANSFER_REFERENCE = 'DERIVED-V045'",
                Integer.class);
        return n != null && n > 0;
    }

    @Test
    @DisplayName("deriving the fund chain put no codes into the chart of accounts")
    void derivationDidNotTouchTheChart() {
        // V045 populates the fund chain and must never populate the chart. Account
        // codes come from the official manual: the economic segment's from V064
        // (the ministry's revenue statement form), nothing from operational data.
        // So the only chart rows allowed are V064's, and the derivation maps
        // nothing to them either.
        Integer outsideTheManual = jdbc.queryForObject("""
                SELECT COUNT(*) FROM FINCORE.COA_ACCOUNT a
                 WHERE a.SEGMENT_ID <> (SELECT SEGMENT_ID FROM FINCORE.COA_SEGMENT WHERE SEGMENT_CODE = 'ECONOMIC')
                    OR a.ACCOUNT_NATURE <> 'REVENUE' OR a.IS_BUDGETARY <> 1 OR a.STUDY_COUNTRY_IN_ID IS NOT NULL
                """, Integer.class);
        assertThat(outsideTheManual)
                .as("a chart row that is not part of the imported revenue manual was invented by something")
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM FINCORE.COA_ACCOUNT_MAP", Integer.class))
                .as("the derivation must not map fee types to accounts it did not have")
                .isZero();
    }

    @Test
    @DisplayName("every derived balance reconciles with the rows behind it")
    void derivedBalancesReconcile() {
        assumeTrue(derivationHasRun(), "V045 has not been run against this database");

        List<Map<String, Object>> drift = jdbc.queryForList("""
                SELECT STUDY_COUNTRY_IN_ID, CURRENCY_ID,
                       TRANSFERRED_DELTA, DISBURSED_DELTA, EARMARKED_DELTA, SPENT_DELTA
                FROM FINCORE.V_ADVANCE_RECONCILIATION
                WHERE TRANSFERRED_DELTA <> 0 OR DISBURSED_DELTA <> 0
                   OR EARMARKED_DELTA  <> 0 OR SPENT_DELTA     <> 0
                """);

        assertThat(drift)
                .as("a stored total that disagrees with its own detail: %s", drift)
                .isEmpty();
    }

    @Test
    @DisplayName("no attaché is shown as having paid out more than it held")
    void noAdvanceIsOverdrawn() {
        assumeTrue(derivationHasRun(), "V045 has not been run against this database");

        List<Map<String, Object>> bad = jdbc.queryForList("""
                SELECT STUDY_COUNTRY_IN_ID, CURRENCY_ID, AVAILABLE, WAFAR_BALANCE
                FROM FINCORE.ATTACHE_ADVANCE_BALANCE
                WHERE AVAILABLE < 0 OR WAFAR_BALANCE < 0
                """);

        assertThat(bad)
                .as("CK_ADV_NOT_OVERDRAWN should make this impossible to store: %s", bad)
                .isEmpty();
    }

    @Test
    @DisplayName("the Wafar chain matches its two independent sources")
    void wafarMatchesItsSources() {
        assumeTrue(derivationHasRun(), "V045 has not been run against this database");

        // STUDENT_FINANCE_WAFAR and STUDENT_FINANCE.WAFAR_AMOUNT are recorded
        // separately and agree at 37,160. The earmarks derived from the first
        // must still equal that, once carry-forward rows are excluded — those
        // represent last year's closing balance, not a new entitlement.
        BigDecimal earmarkedFromSource = jdbc.queryForObject("""
                SELECT ISNULL(SUM(AMOUNT), 0) FROM FINCORE.WAFAR_EARMARK
                WHERE NOTES NOT LIKE N'%carried forward%'
                """, BigDecimal.class);
        BigDecimal sourceDeposits = jdbc.queryForObject(
                "SELECT ISNULL(SUM(STUDENT_FIN_RECIEVED_AMOUNT), 0) FROM MOHECORE.STUDENT_FINANCE_WAFAR",
                BigDecimal.class);

        assertThat(earmarkedFromSource)
                .as("every Wafar deposit in the source must appear as an earmark")
                .isEqualByComparingTo(sourceDeposits);

        // Only APPROVED spending is money that left. Pending, rejected and
        // cancelled rows are decisions; counting them would overstate the spend.
        BigDecimal used = jdbc.queryForObject(
                "SELECT ISNULL(SUM(AMOUNT), 0) FROM FINCORE.WAFAR_USE", BigDecimal.class);
        BigDecimal approvedSpending = jdbc.queryForObject(
                "SELECT ISNULL(SUM(AMOUNT), 0) FROM MOHECORE.WAFAR_SPENDING WHERE STATUS = 'APPROVED'",
                BigDecimal.class);

        assertThat(used)
                .as("Wafar uses must match approved spending exactly, and include nothing else")
                .isEqualByComparingTo(approvedSpending);
    }

    @Test
    @DisplayName("no batch line disburses more than the student was entitled to")
    void noLineOverpays() {
        assumeTrue(derivationHasRun(), "V045 has not been run against this database");

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM FINCORE.PAYMENT_BATCH_LINE
                WHERE DISBURSED_AMOUNT > ENTITLED_AMOUNT
                """, Integer.class))
                .as("the shortfall between the two is exactly what becomes Wafar")
                .isZero();
    }

    @Test
    @DisplayName("the MOHE_QUARTER roll-up equals the allocations behind it")
    void quarterRollUpMatchesAllocations() {
        assumeTrue(derivationHasRun(), "V045 has not been run against this database");

        List<Map<String, Object>> mismatched = jdbc.queryForList("""
                SELECT mq.QUARTER, mq.BUDGET_AMOUNT, x.total
                FROM MOHECORE.MOHE_QUARTER mq
                JOIN (SELECT MOHE_QUARTER_KEY, SUM(AMOUNT) AS total
                      FROM FINCORE.ATTACHE_ALLOCATION WHERE STATUS = 'TRANSFERRED'
                      GROUP BY MOHE_QUARTER_KEY) x ON x.MOHE_QUARTER_KEY = mq.QUARTER
                WHERE mq.BUDGET_AMOUNT IS NULL OR mq.BUDGET_AMOUNT <> x.total
                """);

        assertThat(mismatched)
                .as("""
                    MOHE_QUARTER.BUDGET_AMOUNT was NULL on every row before this; \
                    now that it holds a figure it has to be the right one: %s""", mismatched)
                .isEmpty();
    }

    @Test
    @DisplayName("every derived row is marked as derived, so it can be told from a real record")
    void derivedRowsAreIdentifiable() {
        assumeTrue(derivationHasRun(), "V045 has not been run against this database");

        // The allocations are the inferential part: there is no transfer table,
        // so the amount is deduced. If the real transfer advices ever arrive,
        // these have to be findable and removable without touching anything else.
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM FINCORE.ATTACHE_ALLOCATION
                WHERE TRANSFER_REFERENCE IS NULL OR TRANSFER_REFERENCE <> 'DERIVED-V045'
                """, Integer.class))
                .as("an inferred allocation that is not labelled as one is indistinguishable from a recorded transfer")
                .isZero();
    }
}
