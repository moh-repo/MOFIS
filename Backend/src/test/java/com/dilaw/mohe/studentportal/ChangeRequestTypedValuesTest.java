package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.config.AppTimeZone;
import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.studentportal.model.StudentChangeRequest;
import com.dilaw.mohe.studentportal.repository.StudentChangeRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Change requests carry typed values (review R2b) and a rejection needs a reason (R2c).
 *
 * <p>Each test runs inside a transaction it rolls back itself, under the SYSTEM tenant
 * scope set before the connection is borrowed; no request or student change survives.
 * Needs V115.
 */
@Tag("integration")
@SpringBootTest
class ChangeRequestTypedValuesTest {

    @Autowired private StudentChangeRequestService service;
    @Autowired private StudentChangeRequestRepository requests;
    @Autowired private StudentDataRepository students;
    @Autowired private AppTimeZone appTimeZone;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    /** A student with a portal account, and that account's id. */
    private record PortalStudent(int studentId, int studentUserId) {}

    private void inRolledBackTx(Consumer<PortalStudent> body) {
        TenantContext.runAsSystem(() -> new TransactionTemplate(txManager).execute(status -> {
            status.setRollbackOnly();
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT TOP 1 u.student_user_id, u.student_id FROM STUDENTCORE.student_users u "
                            + "JOIN MOHECORE.STUDENT_DATA s ON s.STUDENT_ID = u.student_id ORDER BY u.student_id");
            body.accept(new PortalStudent(((Number) row.get("student_id")).intValue(),
                    ((Number) row.get("student_user_id")).intValue()));
            return null;
        }));
    }

    private StudentChangeRequest pending(PortalStudent s, String field, String value) {
        StudentChangeRequest r = new StudentChangeRequest();
        r.setStudentId(s.studentId());
        r.setStudentUserId(s.studentUserId());
        r.setSubmissionId("R2B-TEST");
        r.setFieldName(field);
        r.setNewValue(value);
        r.setStatus("PENDING");
        r.setCreatedAt(Instant.now());
        r.setIsDeleted(false);
        return requests.saveAndFlush(r);
    }

    @Test
    @DisplayName("a request stored as 1-1-2000 before this change is approved as 1 January 2000")
    void legacyDateIsApproved() {
        inRolledBackTx(s -> AsUser.run(AsUser.SUPERADMIN, () -> {
            StudentChangeRequest req = pending(s, "studentBirthdate", "1-1-2000");

            StudentChangeRequest done = service.approve(req.getRequestId(), AsUser.SUPERADMIN, null);

            assertThat(done.getAppliedValue()).isEqualTo("2000-01-01");
            StudentDatum student = students.findById(s.studentId()).orElseThrow();
            assertThat(appTimeZone.toLocalDate(student.getStudentBirthdate())).isEqualTo(LocalDate.of(2000, 1, 1));
        }));
    }

    @Test
    @DisplayName("submission stores a date as yyyy-MM-dd and refuses one that is not a date")
    void submissionIsChecked() {
        inRolledBackTx(s -> AsUser.run(AsUser.SUPERADMIN, () -> {
            List<StudentChangeRequest> saved = service.submitRequest(s.studentId(), s.studentUserId(), List.of(
                    new StudentChangeRequestService.FieldChangeInput("studentBirthdate", null, "31/01/2001")));
            assertThat(saved).singleElement().extracting(StudentChangeRequest::getNewValue).isEqualTo("2001-01-31");

            assertThatThrownBy(() -> service.submitRequest(s.studentId(), s.studentUserId(), List.of(
                    new StudentChangeRequestService.FieldChangeInput("studentBirthdate", null, "31-02-2001"))))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("400")
                    .hasMessageContaining("31-02-2001");

            assertThatThrownBy(() -> service.submitRequest(s.studentId(), s.studentUserId(), List.of(
                    new StudentChangeRequestService.FieldChangeInput("studyCountryIn", null, "Yemen"))))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("400");
        }));
    }

    @Test
    @DisplayName("P20 (live QA M18): a lookup id with no row is refused when submitted, not at approval")
    void missingLookupIdIsRefusedOnSubmit() {
        inRolledBackTx(s -> AsUser.run(AsUser.SUPERADMIN, () -> {
            assertThatThrownBy(() -> service.submitRequest(s.studentId(), s.studentUserId(), List.of(
                    new StudentChangeRequestService.FieldChangeInput("studyUniversityIn", null, "99999999"))))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("400")
                    .hasMessageContaining("99999999");
        }));
    }

    @Test
    @DisplayName("a rejection without a reason is refused; with one it is saved")
    void rejectionNeedsReason() {
        inRolledBackTx(s -> AsUser.run(AsUser.SUPERADMIN, () -> {
            StudentChangeRequest req = pending(s, "studentName", "Test Name");

            assertThatThrownBy(() -> service.reject(req.getRequestId(), AsUser.SUPERADMIN, "   "))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("400");
            assertThat(requests.findById(req.getRequestId()).orElseThrow().getStatus()).isEqualTo("PENDING");

            StudentChangeRequest done = service.reject(req.getRequestId(), AsUser.SUPERADMIN, " wrong spelling ");
            assertThat(done.getStatus()).isEqualTo("REJECTED");
            assertThat(done.getRejectionReason()).isEqualTo("wrong spelling");
        }));
    }

    @Test
    @DisplayName("the review list loads a page of pending requests and names each field's type")
    void pendingPageLoads() {
        inRolledBackTx(s -> AsUser.run(AsUser.SUPERADMIN, () -> {
            pending(s, "studentBirthdate", "2000-01-01");

            var page = service.getPendingRequests("PENDING", s.studentId(), null, PageRequest.of(0, 20));

            assertThat(page.getContent()).isNotEmpty()
                    .allSatisfy(dto -> assertThat(dto.getReviewedByName()).isNull());
            assertThat(page.getContent()).filteredOn(dto -> "studentBirthdate".equals(dto.getFieldName()))
                    .allSatisfy(dto -> assertThat(dto.getFieldType()).isEqualTo("DATE"));
        }));
    }
}
