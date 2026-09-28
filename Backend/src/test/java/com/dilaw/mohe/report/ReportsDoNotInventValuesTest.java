package com.dilaw.mohe.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.dilaw.mohe.model.BankTransaction;
import com.dilaw.mohe.repository.BankTransactionRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Report columns must come from the record, not from an assumption.
 *
 * <p>Five views asserted values nothing had recorded: a scholarship amount of
 * {@code STUDENT_STUDY_NO_OF_YEARS * 10000}, a currency of {@code 'USD'}, an
 * auditor named {@code 'System Auditor'}, a transaction direction of
 * {@code 'Debit'}, a performance target of {@code 70}. V034 replaced them —
 * mostly with real columns, and with NULL where the data genuinely is not there.
 *
 * <p>These tests assert what the views <em>return</em>, not what their text
 * says. The first draft of V034's own verification searched sys.sql_modules for
 * the string {@code 'USD'} and reported four views as still guilty; all four
 * were false positives — three were comments explaining the fix, and the fourth
 * was {@code * 1000000} in a surrogate key, which contains {@code * 10000}.
 * A check that cannot distinguish a mistake from a comment about the mistake is
 * not a check.
 */
@Tag("integration")
@SpringBootTest
class ReportsDoNotInventValuesTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BankTransactionRepository bankTransactionRepository;

    @Test
    @DisplayName("a scholarship amount is the student's recorded allowance, not a formula")
    void scholarshipAmount_comesFromTheStudentRecord() {
        Integer mismatched = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM MOHECORE.V_Scholarship_Tracking st
                JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = st.student_id
                WHERE ISNULL(st.scholarship_amount, -1)
                      <> ISNULL(CAST(sd.STUDENT_AMOUNT AS DECIMAL(18,2)), -1)
                """, Integer.class);

        assertThat(mismatched)
                .as("scholarship_amount must equal STUDENT_DATA.STUDENT_AMOUNT. It used to be "
                        + "the student's year count times ten thousand, which agreed with no "
                        + "recorded figure at all")
                .isZero();
    }

    @Test
    @DisplayName("no remaining balance and no payment schedule are reported, because neither is recorded")
    void scholarshipPlaceholders_areGone() {
        // V034 printed both as NULL placeholders; V078 removed the columns (owner decision O1).
        assertThat(columnExists("V_Scholarship_Tracking", "amount_remaining"))
                .as("STUDENT_AMOUNT is a periodic allowance, not a total award, so there is no "
                        + "balance to report. If a total award column is added later, this test "
                        + "should be the thing that fails and gets rewritten.")
                .isFalse();
        assertThat(columnExists("V_Scholarship_Tracking", "payment_schedule")).isFalse();
        assertThat(columnExists("V_Scholarship_Tracking", "scholarship_status_code")).isTrue();
        Integer labelled = jdbc.queryForObject("""
                SELECT COUNT(*) FROM MOHECORE.V_Scholarship_Tracking
                WHERE scholarship_type = 'Other' OR scholarship_provider = 'Other' OR scholarship_status = 'Unknown'
                """, Integer.class);
        assertThat(labelled).as("a missing lookup is NULL, not an English word").isZero();
    }

    @Test
    @DisplayName("the payment method is a code from LU_PAYMENT_METHOD, never a word invented in SQL")
    void paymentMethod_isACodeFromTheTable() {
        assertThat(columnExists("V_Payment_Summary", "payment_status_code")).isTrue();
        Integer unknownMethods = jdbc.queryForObject("""
                SELECT COUNT(*) FROM MOHECORE.V_Payment_Summary p
                WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.LU_PAYMENT_METHOD m WHERE m.CODE = p.payment_method AND m.IS_DELETED = 0)
                """, Integer.class);
        assertThat(unknownMethods).isZero();
        Integer systemMethods = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.LU_PAYMENT_METHOD WHERE IS_SYSTEM = 1 AND IS_DELETED = 0", Integer.class);
        assertThat(systemMethods).as("BANK_TRANSFER, CHECK, CASH").isEqualTo(3);
    }

    @Test
    @DisplayName("a currency column is empty while no currency is recorded")
    void currencyColumns_areNotAsserted() {
        // STUDENT_FINANCE.STUDY_CURRENCY_IN_ID is NULL on every row, so any view
        // reading it must report nothing. A constant here means a literal is back.
        assertThat(distinctNonNull("V_Financial_Audit", "audit_currency"))
                .as("audit_currency asserted 'USD' on a financial audit report")
                .isZero();
        assertThat(distinctNonNull("V_Bank_Transaction", "transaction_currency"))
                .as("transaction_currency asserted 'USD'")
                .isZero();
        assertThat(distinctNonNull("V_Payment_Summary", "payment_currency"))
                .as("payment_currency asserted N'LYD'")
                .isZero();

        // The premise: if this ever becomes non-zero the assertions above are
        // measuring the wrong thing and must be rewritten to compare against the
        // recorded currency rather than expecting emptiness.
        Integer financeRowsWithCurrency = jdbc.queryForObject(
                "SELECT COUNT(STUDY_CURRENCY_IN_ID) FROM MOHECORE.STUDENT_FINANCE", Integer.class);
        assertThat(financeRowsWithCurrency)
                .as("a currency is now recorded on finance rows, so 'the column must be empty' "
                        + "is no longer the right assertion — compare to the lookup instead")
                .isZero();
    }

    @Test
    @DisplayName("no auditor is named where none was recorded")
    void auditorName_isNotInvented() {
        List<Map<String, Object>> named = jdbc.queryForList(
                "SELECT DISTINCT auditor_name FROM MOHECORE.V_Financial_Audit WHERE auditor_name IS NOT NULL");

        assertThat(named)
                .as("auditor_name read 'System Auditor' on every row — a financial audit report "
                        + "naming an accountable party that does not exist. It now reads "
                        + "STUDY_FINANCE_DOF, which is unset on this data.")
                .extracting(row -> String.valueOf(row.get("auditor_name")))
                .doesNotContain("System Auditor");
    }

    @Test
    @DisplayName("a performance target is read from REPORT_METRIC_TARGET, never hardcoded")
    void performanceTarget_comesFromItsTable() {
        List<Map<String, Object>> metrics = jdbc.queryForList(
                "SELECT metric_name, target_value, performance_status FROM MOHECORE.V_Performance_Metrics");

        assertThat(metrics).as("the view must still return its metrics").isNotEmpty();

        Integer configured = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.REPORT_METRIC_TARGET", Integer.class);

        if (configured == 0) {
            assertThat(metrics)
                    .as("with no target configured, nothing may claim one — the graduation "
                            + "metric used to measure itself against a literal 70 and report "
                            + "'below_target' against a number no one had set")
                    .allSatisfy(row -> assertThat(row.get("target_value")).isNull());
            assertThat(metrics)
                    .extracting(row -> String.valueOf(row.get("performance_status")))
                    .doesNotContain("on_target", "below_target");
        } else {
            assertThat(metrics)
                    .as("a target is configured, so at least one metric should be judged against it")
                    .anySatisfy(row -> assertThat(row.get("target_value")).isNotNull());
        }
    }

    @Test
    @DisplayName("a bank transaction direction is derived from a status, never defaulted")
    void transactionDirection_isNotDefaulted() {
        // This reads the entity, because BankTransaction's @Subselect — not the
        // view of the same name — is what the report screen renders. Every branch
        // of its CASE reads a recorded status; the removed ELSE 'Debit' did not,
        // so a row whose status matched nothing was still given a direction of
        // money movement on a bank report.
        List<BankTransaction> rows = bankTransactionRepository.findAll();
        assertThat(rows).as("no transactions to check — this test would prove nothing").isNotEmpty();

        assertThat(rows)
                .as("a direction must be traceable to the row's received-status; "
                        + "where it is not, the column is empty rather than guessing 'Debit'")
                .allSatisfy(t -> {
                    if (t.getTransactionDirection() != null) {
                        assertThat(t.getTransactionDirection()).isIn("Credit", "Debit");
                        assertThat(t.getTransactionType())
                                .as("a direction was derived, so the status it came from must be present")
                                .isNotNull();
                    }
                });
    }

    private Integer distinctNonNull(String view, String column) {
        return jdbc.queryForObject(
                "SELECT COUNT(DISTINCT " + column + ") FROM MOHECORE." + view, Integer.class);
    }

    private boolean columnExists(String view, String column) {
        return jdbc.queryForObject("SELECT CASE WHEN COL_LENGTH('MOHECORE." + view + "', '" + column
                + "') IS NULL THEN 0 ELSE 1 END", Integer.class) == 1;
    }
}
