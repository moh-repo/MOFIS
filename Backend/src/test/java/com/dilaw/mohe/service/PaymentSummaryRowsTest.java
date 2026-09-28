package com.dilaw.mohe.service;

import com.dilaw.mohe.model.PaymentSummary;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Every Payment Summary page row is one real finance row, each exactly once
 * (QA round 5, R5-05).
 *
 * <p>The rows were mapped onto an entity keyed by {@code STUDENT_FIN_ID}, a
 * reference typed in by the user that may be blank and that several students may
 * share. Hibernate returns {@code null} for a row whose key is NULL and folds rows
 * sharing a key into the first one, so the page held nulls — the screen then threw
 * on {@code paymentAmount} and showed a total of 0 — and repeated one student's
 * payment in place of another's. A finance row's key is {@code STUDENT_ID}.
 *
 * <p>Each case pages through the whole result as the account would and compares it
 * with the base tables read under the same scope.
 */
@Tag("integration")
@SpringBootTest
class PaymentSummaryRowsTest {

    private static final int JORDAN = 1004;

    /** The screen's default order; most rows have no payment date, so it is mostly ties. */
    private static final Sort DEFAULT_SORT = Sort.by("payment_date").descending();

    @Autowired private PaymentSummaryService service;
    @Autowired private JdbcTemplate jdbc;

    /** A finance row as the base tables hold it. */
    private record Expected(String reference, BigDecimal amount, String statusCode) {}

    @ParameterizedTest(name = "page size {0}")
    @ValueSource(ints = {20, 50})
    @DisplayName("superadmin: every page row is a real finance row, each exactly once")
    void superadminPagesHoldEveryRowOnce(int size) {
        assertPagesHoldEveryRowOnce(AsUser.SUPERADMIN, null, size);
    }

    @ParameterizedTest(name = "user {0}, country {1}, page size {2}")
    @CsvSource({"13, 1004, 20", "13, 1004, 10", "10, 1005, 20"})
    @DisplayName("country account: only its own country's rows, each exactly once")
    void countryPagesHoldOnlyTheirRowsOnce(long user, int country, int size) {
        assertPagesHoldEveryRowOnce(user, country, size);
    }

    @Test
    @Transactional
    @DisplayName("a finance row with no reference, or one shared with another student, is still its own row")
    void blankOrSharedReferenceIsStillItsOwnRow() {
        List<Integer> students = jdbc.queryForList("""
                SELECT TOP 3 sf.STUDENT_ID
                  FROM MOHECORE.STUDENT_FINANCE sf
                  JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = sf.STUDENT_ID AND ISNULL(sd.IS_DELETED, 0) = 0
                 WHERE sd.STUDY_COUNTRY_IN = ?
                 ORDER BY sf.STUDENT_ID
                """, Integer.class, JORDAN);
        assumeTrue(students.size() == 3, "needs three live Jordanian finance rows");

        // Rolled back with the test: the two shapes the live data showed.
        jdbc.update("UPDATE MOHECORE.STUDENT_FINANCE SET STUDENT_FIN_ID = NULL WHERE STUDENT_ID = ?",
                students.get(0));
        jdbc.update("UPDATE MOHECORE.STUDENT_FINANCE SET STUDENT_FIN_ID = N'990001' WHERE STUDENT_ID IN (?, ?)",
                students.get(1), students.get(2));

        assertPagesHoldEveryRowOnce(AsUser.JO_ADMIN, JORDAN, 20);
    }

    private void assertPagesHoldEveryRowOnce(long user, Integer country, int size) {
        Map<Integer, Expected> expected = expectedRows(user, country);
        assumeTrue(!expected.isEmpty(), "no live finance rows in this scope");

        List<PaymentSummary> rows = allPages(user, size);

        assertThat(rows).extracting(PaymentSummary::getStudentId)
                .as("each of the caller's finance rows once, and no other")
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(expected.keySet());

        for (PaymentSummary row : rows) {
            Expected own = expected.get(row.getStudentId());
            assertThat(row.getPaymentId())
                    .as("student %d shows its own reference", row.getStudentId())
                    .isEqualTo(own.reference() == null ? null : Integer.valueOf(own.reference().trim()));
            assertThat(row.getPaymentAmount())
                    .as("student %d shows its own amount", row.getStudentId())
                    .isEqualTo(own.amount());
            assertThat(row.getPaymentStatusCode())
                    .as("student %d shows its own status", row.getStudentId())
                    .isEqualTo(own.statusCode());
        }

        // What the screen's Total Amount adds up.
        assertThat(sum(rows.stream().map(PaymentSummary::getPaymentAmount).toList()))
                .isEqualByComparingTo(sum(expected.values().stream().map(Expected::amount).toList()));
    }

    /** Pages through the whole result as the screen would, checking each page on the way. */
    private List<PaymentSummary> allPages(long user, int size) {
        return AsUser.call(user, () -> {
            List<PaymentSummary> rows = new ArrayList<>();
            Page<PaymentSummary> page;
            int number = 0;
            do {
                page = service.getByFilters(null, null, null, null, null, null, null, null, null, null,
                        PageRequest.of(number++, size, DEFAULT_SORT));
                long expectedSize = Math.min(size, page.getTotalElements() - (long) page.getNumber() * size);
                assertThat(page.getContent())
                        .as("page %d of size %d", page.getNumber(), size)
                        .doesNotContainNull()
                        .hasSize((int) expectedSize);
                rows.addAll(page.getContent());
            } while (page.hasNext());
            return rows;
        });
    }

    /** The caller's live finance rows, read from the base tables under the caller's own scope. */
    private Map<Integer, Expected> expectedRows(long user, Integer country) {
        String sql = """
                SELECT sf.STUDENT_ID, sf.STUDENT_FIN_ID, sf.NET_AMOUNT, sfrs.CODE
                  FROM MOHECORE.STUDENT_FINANCE sf
                  JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = sf.STUDENT_ID AND ISNULL(sd.IS_DELETED, 0) = 0
                  LEFT JOIN MOHECORE.STUDY_FIN_RECEIVED_STATUS sfrs
                         ON sfrs.STUDY_FIN_RECEIVED_STATUS_ID = sf.STUDY_FIN_RECIEVED_STATUS
                """ + (country == null ? "" : " WHERE sd.STUDY_COUNTRY_IN = ?");
        Object[] args = country == null ? new Object[0] : new Object[] {country};
        return AsUser.call(user, () -> {
            Map<Integer, Expected> rows = new HashMap<>();
            jdbc.query(sql, rs -> {
                rows.put(rs.getInt(1), new Expected(rs.getString(2), rs.getBigDecimal(3), rs.getString(4)));
            }, args);
            return rows;
        });
    }

    private static BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
