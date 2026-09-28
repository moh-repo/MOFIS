package com.dilaw.mohe.controler;

import com.dilaw.mohe.model.PaymentSummary;
import com.dilaw.mohe.repository.PaymentSummaryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Every sort option the payment-summary endpoint accepts must produce SQL the
 * database will run.
 *
 * <p>The endpoint backs onto a native query, and Spring Data appends the sort
 * property to native SQL verbatim rather than translating it. Sorting by the
 * entity property therefore emitted {@code order by sf.paymentDate}, which is
 * not a column on STUDENT_FINANCE — "Invalid column name 'paymentDate'". Every
 * one of the ten options was broken, and since paymentDate is the default the
 * page failed to load at all.
 *
 * <p>Only a real database can confirm the generated SQL parses, so this runs
 * the repository rather than asserting on strings.
 */
@Tag("integration")
@SpringBootTest
class PaymentSummarySortTest {

    @Autowired
    private PaymentSummaryRepository repository;

    @ParameterizedTest(name = "sortBy={0}")
    @ValueSource(strings = {
            "paymentId", "studentId", "studentName", "paymentDate", "paymentAmount",
            "paymentStatus", "paymentType", "paymentMethod", "quarterName", "yearName"})
    @DisplayName("each accepted sort option produces runnable SQL")
    void everySortOption_executes(String sortBy) {
        String column = PaymentSummaryController.paymentSortColumn(sortBy);

        assertThatCode(() -> query(column))
                .as("sortBy=%s maps to column '%s'", sortBy, column)
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "direction={0}")
    @ValueSource(strings = {"ASC", "DESC"})
    @DisplayName("the default sort works in both directions and returns rows")
    void defaultSort_returnsRows(String direction) {
        Sort sort = "DESC".equals(direction)
                ? Sort.by("payment_date").descending()
                : Sort.by("payment_date").ascending();

        Page<PaymentSummary> page = repository.findByFilters(
                null, null, null, null, null, null, null,
                false, List.of(0), null, PageRequest.of(0, 20, sort));

        assertThat(page).isNotNull();
    }

    /** An unrecognised sort name must fall back, never reach the database raw. */
    @org.junit.jupiter.api.Test
    @DisplayName("an unknown sort name falls back to the default column")
    void unknownSort_fallsBack() {
        assertThat(PaymentSummaryController.paymentSortColumn("'; DROP TABLE x--"))
                .isEqualTo("payment_date");
        assertThat(PaymentSummaryController.paymentSortColumn(null))
                .isEqualTo("payment_date");
        // Callers already passing the alias keep working.
        assertThat(PaymentSummaryController.paymentSortColumn("student_name"))
                .isEqualTo("student_name");
    }

    private void query(String column) {
        repository.findByFilters(null, null, null, null, null, null, null,
                false, List.of(0), null,
                PageRequest.of(0, 5, Sort.by(column).descending()));
    }
}
