package com.dilaw.mohe.controler;

import com.dilaw.mohe.approval.event.ApprovalExecutionEvent;
import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.model.StudentBooksAllowance;
import com.dilaw.mohe.repository.StudentBooksAllowanceRepository;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What an approved books allowance change does to the row.
 *
 * <p>The screen is behind the approval workflow now, so a POST or PUT writes
 * nothing — that half is {@link FinanceApprovalSubmissionTest}. Held here is
 * what an approver's sign-off applies, and the faults the page was fixed
 * against must keep holding through it:
 *
 * <ul>
 *   <li>{@code booksAllowId: 0} on an identity column, which made Spring Data
 *       take {@code merge()} rather than {@code persist()} and answered 409 on
 *       every single Add;</li>
 *   <li>{@code "midate": null} against {@code MIDATE datetime2 NOT NULL}, whose
 *       {@code sysutcdatetime()} default cannot fire for a column the INSERT
 *       names;</li>
 *   <li>{@code update} as {@code repository.save(entity)} on a bound entity, so
 *       every edit wrote NULL over each column the dialog does not send. <b>A
 *       field that is absent is not a field set to nothing.</b></li>
 * </ul>
 *
 * <p>Every row created here is removed in a {@code finally}, and each is named
 * with a {@code ZT-} marker so anything a crash leaves behind is recognisable.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class StudentBooksAllowanceCrudTest {

    private static final List<String> FINANCE = List.of("FINANCE.VIEW", "FINANCE.EDIT");

    @Autowired private StudentBooksAllowanceRepository repository;
    @Autowired private ApprovalIntegrationService integration;
    @Autowired private JdbcTemplate jdbc;

    /** Applies a request the way an approver's final sign-off does. */
    private void execute(RequestType type, Integer entityId, String requestData) {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(1L);
        request.setEntityType("STUDENT_BOOKS_ALLOWANCE");
        request.setRequestType(type);
        request.setEntityId(entityId == null ? null : String.valueOf(entityId));
        request.setRequestData(requestData);
        request.setRequestedBy(1L);
        AsUser.callWithAuthorities(AsUser.SUPERADMIN, FINANCE, () -> {
            integration.handleApprovalExecution(new ApprovalExecutionEvent(this, request, 1L));
            return null;
        });
    }

    /**
     * The country stored on the row, read from the column rather than the entity.
     *
     * <p>{@code STUDY_COUNTRY_IN_ID} is a real FK that the entity did not map, so
     * this asks the database directly — which is also what makes the assertion
     * hold whether or not the mapping is there.
     */
    private Integer storedCountry(Integer id) {
        return jdbc.queryForObject(
                "SELECT STUDY_COUNTRY_IN_ID FROM MOHECORE.STUDENT_BOOKS_ALLOWANCE WHERE BOOKS_ALLOW_ID = ?",
                Integer.class, id);
    }

    /** A student that really exists, so the country can be resolved from them. */
    private Integer aStudent() {
        return jdbc.queryForObject(
                "SELECT TOP 1 STUDENT_ID FROM MOHECORE.STUDENT_DATA WHERE STUDY_COUNTRY_IN IS NOT NULL ORDER BY STUDENT_ID",
                Integer.class);
    }

    /**
     * Exactly what the dialog sends. The dialog no longer sends
     * {@code booksAllowId} or {@code midate}; both are here anyway, because a
     * stored request could carry them and the entity must refuse them either way.
     */
    private String dialogBody(Integer studentId, String notes) {
        return """
                {"booksAllowId":0,"studentId":%d,"studyYear":"2024-2025","moheQuarterId":null,
                 "allowanceAmount":375,"payStatus":0,"receiptDate":null,"notes":%s,"midate":null}
                """.formatted(studentId, notes == null ? "null" : "\"" + notes + "\"");
    }

    /** Applies an approved INSERT and returns the id of the row it created. */
    private Integer createApproved(Integer studentId, String marker) {
        execute(RequestType.INSERT, null, dialogBody(studentId, marker));
        return repository.findAll().stream()
                .filter(r -> marker.equals(r.getNotes()))
                .map(StudentBooksAllowance::getBooksAllowId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the approved insert wrote no row"));
    }

    @Test
    @DisplayName("an approved allowance is written, and the dialog's booksAllowId: 0 does not become its id")
    void approvedInsertWritesTheRow() {
        Integer studentId = aStudent();
        Integer id = createApproved(studentId, "ZT-books-insert");
        try {
            assertThat(id).as("a real identity value, not the body's 0").isPositive();

            StudentBooksAllowance saved = repository.findById(id).orElseThrow();
            assertThat(saved.getStudentId()).isEqualTo(studentId);
            assertThat(saved.getAllowanceAmount()).isEqualTo(375);
            assertThat(saved.getStudyYear()).isEqualTo("2024-2025");
        } finally {
            repository.deleteById(id);
        }
    }

    @Test
    @DisplayName("MIDATE is stamped by the server and a request cannot null it")
    void midateIsNeverNull() {
        Integer id = createApproved(aStudent(), "ZT-books-midate");
        try {
            // The column is NOT NULL, so a row that exists at all already proves the
            // insert did not carry the payload's null. This says which value won.
            assertThat(repository.findById(id).orElseThrow().getMidate())
                    .as("MIDATE after an approved insert").isNotNull();

            execute(RequestType.UPDATE, id, "{\"allowanceAmount\":400,\"midate\":null}");

            assertThat(repository.findById(id).orElseThrow().getMidate())
                    .as("MIDATE after an approved edit that sent null").isNotNull();
        } finally {
            repository.deleteById(id);
        }
    }

    @Test
    @DisplayName("an approved edit leaves the columns the dialog never sends alone")
    void editKeepsUnsentColumns() {
        Integer studentId = aStudent();
        Integer id = createApproved(studentId, "ZT-books-edit");
        try {
            Integer countryBefore = storedCountry(id);

            // What the dialog binds, and not one column more.
            String body = """
                    {"booksAllowId":%d,"studentId":%d,"studyYear":"2025-2026","moheQuarterId":null,
                     "allowanceAmount":500,"payStatus":1,"receiptDate":null,"notes":"ZT-books-edited"}
                    """.formatted(id, studentId);
            execute(RequestType.UPDATE, id, body);

            StudentBooksAllowance after = repository.findById(id).orElseThrow();
            assertThat(after.getAllowanceAmount()).isEqualTo(500);
            assertThat(after.getStudyYear()).isEqualTo("2025-2026");
            assertThat(after.getNotes()).isEqualTo("ZT-books-edited");

            // Never sent by the dialog, and so never cleared by it.
            assertThat(storedCountry(id))
                    .as("STUDY_COUNTRY_IN_ID").isEqualTo(countryBefore).isNotNull();
            assertThat(after.getMidate()).as("MIDATE").isNotNull();
        } finally {
            repository.deleteById(id);
        }
    }

    @Test
    @DisplayName("an approved edit still clears a field it does send as null")
    void editStillClearsWhatItOwns() {
        Integer id = createApproved(aStudent(), "ZT-books-clear");
        try {
            execute(RequestType.UPDATE, id, "{\"notes\":null}");

            StudentBooksAllowance after = repository.findById(id).orElseThrow();
            assertThat(after.getNotes()).as("a null the dialog sent").isNull();
            assertThat(storedCountry(id)).as("a column it did not send").isNotNull();
        } finally {
            repository.deleteById(id);
        }
    }

    @Test
    @DisplayName("an approved delete removes the row")
    void deleteRemovesTheRow() {
        Integer id = createApproved(aStudent(), "ZT-books-delete");
        boolean removed = false;
        try {
            execute(RequestType.DELETE, id, "{}");
            removed = true;

            assertThat(repository.findById(id)).isEmpty();
        } finally {
            if (!removed) repository.deleteById(id);
        }
    }

    @Test
    @DisplayName("a student whose country cannot be resolved is refused")
    void unknownStudentIsRefused() {
        // The country comes from the student. When there is no such student there
        // is no country, and the row would be stored under none — invisible to
        // every attaché's staff, which is the disappearance the check exists for.
        assertThatThrownBy(() -> execute(RequestType.INSERT, null, dialogBody(99_999_999, "ZT-books-orphan")))
                .hasMessageContaining("could not be found");

        assertThat(repository.findAll())
                .as("nothing was written")
                .noneMatch(r -> "ZT-books-orphan".equals(r.getNotes()));
    }
}
