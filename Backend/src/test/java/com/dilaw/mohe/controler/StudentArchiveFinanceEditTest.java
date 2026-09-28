package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.model.StudentDataArch;
import com.dilaw.mohe.repository.StudentDataArchRepository;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code PUT /studentDataArch/{id}/finance}, the finance history page's save
 * (review F5; plan W4-10).
 *
 * <p>The page corrects the finance columns of an archived quarter — the amount, its
 * two currencies, the first receipt and the two notes — and nothing else: the rest of
 * the row is a record of what was on file that quarter. {@code FinanceInputRulesTest}
 * holds the copy rule on two objects; this holds it on the table, by reading the whole
 * archived row before and after a save whose body changes every column it can, and
 * holds the gate: {@code FINANCE_ARCH.EDIT} or {@code ARCHIVING.EDIT}, not a reader.
 *
 * <p>The save runs inside a transaction the test rolls back itself, so the archived
 * row is as it was afterwards.
 */
@Tag("integration")
@SpringBootTest
class StudentArchiveFinanceEditTest {

    private static final int NO_SUCH_ID = -424242;

    /** The columns the page edits; every other column must come back as it was. */
    private static final Set<String> FINANCE_COLUMNS = Set.of(
            "STUDENT_AMOUNT", "AMOUNT_CURRENCY", "UNIVERSITY_FEE_STATUS_CURRENCY",
            "STUDENT_FIRST_RECEIPT_OF_DUE", "STUDENT_NOTE_1", "STUDENT_NOTE_2");

    @Autowired private StudentDataArchController controller;
    @Autowired private StudentDataArchRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    private <T> T rolledBack(Supplier<T> body) {
        return TenantContext.runAsSystem(() -> new TransactionTemplate(txManager).execute(status -> {
            status.setRollbackOnly();
            return body.get();
        }));
    }

    private Integer anArchivedRow() {
        return jdbc.queryForObject("SELECT TOP 1 ID FROM MOHECORE.STUDENT_DATA_ARCH ORDER BY ID DESC", Integer.class);
    }

    private Map<String, Object> row(Integer id) {
        return new LinkedHashMap<>(jdbc.queryForMap("SELECT * FROM MOHECORE.STUDENT_DATA_ARCH WHERE ID = ?", id));
    }

    /** A body that sets new finance values and tries to change the snapshot's identity as well. */
    private static StudentDataArch everythingChanged(StudentDataArch stored) {
        StudentDataArch body = new StudentDataArch();
        body.setId(NO_SUCH_ID);
        body.setStudentId(NO_SUCH_ID);
        body.setStudentName("Renamed by the finance page");
        body.setStudentPassportNumber("P-CHANGED");
        body.setStudyCountryIn(1004);
        body.setStudentAddedQuarter(99);
        body.setArchiveDate(Instant.parse("2001-01-01T00:00:00Z"));
        body.setStudentAmount(stored.getStudentAmount() == null ? 1234 : stored.getStudentAmount() + 1234);
        body.setAmountCurrency(stored.getAmountCurrency() == null || stored.getAmountCurrency() != 2 ? 2 : 3);
        body.setUniversityFeeStatusCurrency(
                stored.getUniversityFeeStatusCurrency() == null || stored.getUniversityFeeStatusCurrency() != 2 ? 2 : 3);
        body.setStudentFirstReceiptOfDue(
                stored.getStudentFirstReceiptOfDue() == null ? 77 : stored.getStudentFirstReceiptOfDue() + 77);
        body.setStudentNote1("W4-10 note one");
        body.setStudentNote2("W4-10 note two");
        return body;
    }

    @Test
    @DisplayName("the save copies the six finance columns and leaves every other column of the archived row as it was")
    void onlyFinanceColumnsChange() {
        Integer id = anArchivedRow();
        assertThat(id).as("the archive has a row to edit").isNotNull();

        Map<String, Object>[] beforeAndAfter = rolledBack(() -> {
            Map<String, Object> before = row(id);
            StudentDataArch body = everythingChanged(repository.findById(id).orElseThrow());

            StudentDataArch answer = AsUser.callWithAuthorities(AsUser.SUPERADMIN, List.of("FINANCE_ARCH.EDIT"),
                    () -> controller.updateFinance(id, body));
            assertThat(answer.getId()).as("the path names the row, whatever the body says").isEqualTo(id);

            repository.flush();
            @SuppressWarnings("unchecked")
            Map<String, Object>[] pair = new Map[] {before, row(id), Map.of(
                    "STUDENT_AMOUNT", body.getStudentAmount(),
                    "AMOUNT_CURRENCY", body.getAmountCurrency(),
                    "UNIVERSITY_FEE_STATUS_CURRENCY", body.getUniversityFeeStatusCurrency(),
                    "STUDENT_FIRST_RECEIPT_OF_DUE", body.getStudentFirstReceiptOfDue(),
                    "STUDENT_NOTE_1", body.getStudentNote1(),
                    "STUDENT_NOTE_2", body.getStudentNote2())};
            return pair;
        });
        Map<String, Object> before = beforeAndAfter[0];
        Map<String, Object> after = beforeAndAfter[1];
        Map<String, Object> sent = beforeAndAfter[2];

        for (String column : FINANCE_COLUMNS) {
            assertThat(String.valueOf(after.get(column))).as("finance column %s takes the value sent", column)
                    .isEqualTo(String.valueOf(sent.get(column)));
        }
        before.keySet().removeAll(FINANCE_COLUMNS);
        after.keySet().removeAll(FINANCE_COLUMNS);
        assertThat(after).as("every other column of the archived row").isEqualTo(before);
    }

    @Test
    @DisplayName("a negative amount is refused and the row is not touched")
    void negativeAmountIsRefused() {
        Integer id = anArchivedRow();
        Object stored = rolledBack(() -> {
            StudentDataArch body = new StudentDataArch();
            body.setStudentAmount(-1);
            assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.SUPERADMIN, List.of("FINANCE_ARCH.EDIT"),
                    () -> controller.updateFinance(id, body)))
                    .isInstanceOf(IllegalArgumentException.class);
            return row(id).get("STUDENT_AMOUNT");
        });
        assertThat(stored).isEqualTo(jdbc.queryForObject(
                "SELECT STUDENT_AMOUNT FROM MOHECORE.STUDENT_DATA_ARCH WHERE ID = ?", Object.class, id));
    }

    @Test
    @DisplayName("FINANCE_ARCH.EDIT or ARCHIVING.EDIT may save; the readers of the page may not")
    void gate() {
        for (List<String> reader : List.of(
                List.of("FINANCE_ARCH.VIEW", "FINANCE_ARCH.EXPORT"),
                List.of("ARCHIVING.VIEW", "STUDENT_HISTORY.VIEW"),
                List.of("FINANCE.VIEW", "FINANCE.EDIT"))) {
            assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.SUPERADMIN, reader,
                    () -> controller.updateFinance(NO_SUCH_ID, new StudentDataArch())))
                    .as("finance save with only %s", reader)
                    .isInstanceOf(AccessDeniedException.class);
        }

        // Past the gate the unknown id is a 404 — nothing is written by the positive controls.
        for (String editor : List.of("FINANCE_ARCH.EDIT", "ARCHIVING.EDIT")) {
            assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.SUPERADMIN, List.of(editor),
                    () -> controller.updateFinance(NO_SUCH_ID, new StudentDataArch())))
                    .as("positive control: %s gets past the gate", editor)
                    .isNotInstanceOf(AccessDeniedException.class)
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Test
    @DisplayName("another attaché's archived row cannot be corrected: 403, and it is unchanged")
    void scoped() {
        Integer id = anArchivedRow();
        Integer country = jdbc.queryForObject(
                "SELECT STUDY_COUNTRY_IN FROM MOHECORE.STUDENT_DATA_ARCH WHERE ID = ?", Integer.class, id);
        assertThat(country).as("the fixture row would have to be outside Jordan for this to mean anything")
                .isNotEqualTo(1004);

        Object stored = rolledBack(() -> {
            StudentDataArch body = new StudentDataArch();
            body.setStudentAmount(1);
            assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, List.of("FINANCE_ARCH.EDIT"),
                    () -> controller.updateFinance(id, body)))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                    .hasMessageContaining("country not in scope");
            return row(id).get("STUDENT_AMOUNT");
        });
        assertThat(stored).isEqualTo(jdbc.queryForObject(
                "SELECT STUDENT_AMOUNT FROM MOHECORE.STUDENT_DATA_ARCH WHERE ID = ?", Object.class, id));
    }
}
