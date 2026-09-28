package com.dilaw.mohe.controler;

import com.dilaw.mohe.approval.event.ApprovalExecutionEvent;
import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.model.StudentLoan;
import com.dilaw.mohe.repository.StudentLoanRepository;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What an approved loan change does to the row.
 *
 * <p>The screen is behind the approval workflow, so a POST or PUT no longer
 * writes anything — that half is {@link FinanceApprovalSubmissionTest}. What is
 * held here is the other half: once an approver signs off, the executor applies
 * the change, and every rule the screen's own faults were fixed against still
 * runs. Those faults were:
 *
 * <ul>
 *   <li>{@code loanId: 0} on an identity column, which made Spring Data take
 *       {@code merge()} rather than {@code persist()} and answered 409 on every
 *       single Add;</li>
 *   <li>{@code "midate": null} against {@code MIDATE datetime2 NOT NULL}, whose
 *       {@code sysutcdatetime()} default cannot fire for a column the INSERT
 *       names;</li>
 *   <li>an amount of {@code 0}, the dialog's own default, refused by
 *       {@code CK_STUDENT_LOAN_AMOUNT_POSITIVE};</li>
 *   <li>{@code update} as {@code repository.save(entity)} on a bound entity, so
 *       every edit wrote NULL over each column the dialog does not send. <b>A
 *       field that is absent is not a field set to nothing.</b></li>
 * </ul>
 *
 * <p>Driven through {@code handleApprovalExecution} rather than the service
 * beans, because that path — request JSON in, row out — is now the only way a
 * change reaches the table. Every row created here is removed in a
 * {@code finally}, and each is named with a {@code ZT-} marker so anything a
 * crash leaves behind is recognisable.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class StudentLoanCrudTest {

    private static final List<String> FINANCE = List.of("FINANCE.VIEW", "FINANCE.EDIT");

    @Autowired private MockMvc mvc;
    @Autowired private StudentLoanRepository repository;
    @Autowired private ApprovalIntegrationService integration;
    @Autowired private JdbcTemplate jdbc;

    /**
     * Applies a request the way an approver's final sign-off does.
     *
     * <p>The request is a plain object rather than a stored row: the executor
     * reads only the entity type, the operation, the id and the payload, and
     * building it here keeps the test on the executor instead of on the
     * multi-level workflow that decides when to call it.
     */
    private void execute(RequestType type, Integer entityId, String requestData) {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(1L);
        request.setEntityType("STUDENT_LOAN");
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
                "SELECT STUDY_COUNTRY_IN_ID FROM MOHECORE.STUDENT_LOAN WHERE LOAN_ID = ?",
                Integer.class, id);
    }

    /** A student that really exists, so the country can be resolved from them. */
    private Integer aStudent() {
        return jdbc.queryForObject(
                "SELECT TOP 1 STUDENT_ID FROM MOHECORE.STUDENT_DATA WHERE STUDY_COUNTRY_IN IS NOT NULL ORDER BY STUDENT_ID",
                Integer.class);
    }

    /**
     * Exactly what the dialog sends — the payload the request stores verbatim.
     * The dialog no longer sends {@code loanId} or {@code midate}; both are here
     * anyway, because a stored request could carry them and the entity must
     * refuse them either way.
     */
    private String dialogBody(Integer studentId, Integer amount, String notes) {
        return """
                {"loanId":0,"studentId":%d,"studyYear":"2024-2025","moheQuarterId":null,
                 "loanAmount":%d,"currencyId":1,"loanStatus":0,"repaymentNotes":%s,"midate":null}
                """.formatted(studentId, amount, notes == null ? "null" : "\"" + notes + "\"");
    }

    /** Applies an approved INSERT and returns the id of the row it created. */
    private Integer createApproved(Integer studentId, String marker) {
        execute(RequestType.INSERT, null, dialogBody(studentId, 4500, marker));
        return repository.findAll().stream()
                .filter(r -> marker.equals(r.getRepaymentNotes()))
                .map(StudentLoan::getLoanId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the approved insert wrote no row"));
    }

    @Test
    @DisplayName("an approved loan is written, and the dialog's loanId: 0 does not become its id")
    void approvedInsertWritesTheRow() {
        Integer studentId = aStudent();
        Integer id = createApproved(studentId, "ZT-loan-insert");
        try {
            assertThat(id).as("a real identity value, not the body's 0").isPositive();

            StudentLoan saved = repository.findById(id).orElseThrow();
            assertThat(saved.getStudentId()).isEqualTo(studentId);
            assertThat(saved.getLoanAmount()).isEqualTo(4500);
            assertThat(saved.getStudyYear()).isEqualTo("2024-2025");
        } finally {
            repository.deleteById(id);
        }
    }

    @Test
    @DisplayName("MIDATE is stamped by the server and a request cannot null it")
    void midateIsNeverNull() {
        Integer id = createApproved(aStudent(), "ZT-loan-midate");
        try {
            // The column is NOT NULL, so a row that exists at all already proves the
            // insert did not carry the payload's null. This says which value won.
            assertThat(repository.findById(id).orElseThrow().getMidate())
                    .as("MIDATE after an approved insert").isNotNull();

            execute(RequestType.UPDATE, id, "{\"loanAmount\":5000,\"midate\":null}");

            assertThat(repository.findById(id).orElseThrow().getMidate())
                    .as("MIDATE after an approved edit that sent null").isNotNull();
        } finally {
            repository.deleteById(id);
        }
    }

    @Test
    @DisplayName("the row is filed under the student's country")
    void countryIsStoredOnCreate() {
        Integer studentId = aStudent();
        Integer id = createApproved(studentId, "ZT-loan-country");
        try {
            Integer expected = jdbc.queryForObject(
                    "SELECT STUDY_COUNTRY_IN FROM MOHECORE.STUDENT_DATA WHERE STUDENT_ID = ?",
                    Integer.class, studentId);
            // Nothing on the screen names a country: it belongs to the student, and a
            // row stored under none is a row no attaché's staff would ever see again.
            assertThat(storedCountry(id)).as("STUDY_COUNTRY_IN_ID").isEqualTo(expected);
        } finally {
            repository.deleteById(id);
        }
    }

    @Test
    @DisplayName("an approved edit leaves the columns the dialog never sent alone")
    void editKeepsUnsentColumns() {
        Integer studentId = aStudent();
        Integer id = createApproved(studentId, "ZT-loan-edit");
        try {
            Integer countryBefore = storedCountry(id);

            // What the dialog binds, and not one column more.
            String body = """
                    {"loanId":%d,"studentId":%d,"studyYear":"2025-2026","moheQuarterId":null,
                     "loanAmount":6000,"currencyId":1,"loanStatus":2,"repaymentNotes":"ZT-loan-edited"}
                    """.formatted(id, studentId);
            execute(RequestType.UPDATE, id, body);

            StudentLoan after = repository.findById(id).orElseThrow();
            assertThat(after.getLoanAmount()).isEqualTo(6000);
            assertThat(after.getStudyYear()).isEqualTo("2025-2026");
            assertThat(after.getLoanStatus()).isEqualTo(2);
            assertThat(after.getRepaymentNotes()).isEqualTo("ZT-loan-edited");

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
        Integer id = createApproved(aStudent(), "ZT-loan-clear");
        try {
            execute(RequestType.UPDATE, id, "{\"repaymentNotes\":null}");

            StudentLoan after = repository.findById(id).orElseThrow();
            assertThat(after.getRepaymentNotes()).as("a null the dialog sent").isNull();
            assertThat(storedCountry(id)).as("a column it did not send").isNotNull();
        } finally {
            repository.deleteById(id);
        }
    }

    @Test
    @DisplayName("an approved delete removes the row")
    void deleteRemovesTheRow() {
        Integer id = createApproved(aStudent(), "ZT-loan-delete");
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
        assertThatThrownBy(() -> execute(RequestType.INSERT, null, dialogBody(99_999_999, 4500, "ZT-loan-orphan")))
                .hasMessageContaining("could not be found");

        assertThat(repository.findAll())
                .as("nothing was written")
                .noneMatch(r -> "ZT-loan-orphan".equals(r.getRepaymentNotes()));
    }

    @Test
    @DisplayName("an amount of zero is refused, not left to the check constraint")
    void zeroAmountIsRefused() {
        // CK_STUDENT_LOAN_AMOUNT_POSITIVE refuses it, and the dialog's default
        // amount used to be 0 — so the screen's out-of-the-box Add hit a
        // constraint the browser could only show as "failed to save".
        assertThatThrownBy(() -> execute(RequestType.INSERT, null, dialogBody(aStudent(), 0, "ZT-loan-zero")))
                .hasMessageContaining("greater than zero");

        assertThat(repository.findAll())
                .as("nothing was written")
                .noneMatch(r -> "ZT-loan-zero".equals(r.getRepaymentNotes()));
    }

    @Test
    @DisplayName("the read paths are untouched by the workflow")
    void readsStillAnswer() throws Exception {
        Integer id = createApproved(aStudent(), "ZT-loan-read");
        try {
            AsUser.callWithAuthorities(AsUser.SUPERADMIN, FINANCE, () -> {
                try {
                    assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/v1/studentLoan/" + id)).andReturn().getResponse().getStatus())
                            .isEqualTo(200);
                    return null;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        } finally {
            repository.deleteById(id);
        }
    }
}
