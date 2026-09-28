package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code GET /studentFinance/student/{id}/new-record}: what the new finance record
 * form needs once a student is picked (review F3, F4; plan W4-10).
 *
 * <p>The defaults the form fills in — amount, study country, the quarter the student
 * was added in — and the two reasons a new record would be refused: the student
 * already has one (a finance record is keyed by the student and edited, not added
 * again), or one is awaiting a decision, whose request the form offers to open. The
 * finance roles hold neither the student permissions nor APPROVALS, so this is the
 * only place they can read either answer; it answers those few fields and no more,
 * for a student in the caller's scope, behind the finance gate.
 *
 * <p>STUDENT_DATA and STUDENT_FINANCE are only read. The approval requests raised
 * here carry a request body nothing else writes and are removed afterwards.
 */
@Tag("integration")
@SpringBootTest
class FinanceNewRecordDefaultsTest {

    private static final int NO_SUCH_ID = -424242;
    private static final String FIXTURE_BODY = "{\"w410NewRecordTest\":true}";
    private static final List<String> FINANCE_READER = List.of("FINANCE.VIEW");

    /** Everything the endpoint answers; a new field here is a new disclosure to a finance role. */
    private static final String[] ANSWER_KEYS = {
            "studentId", "studentName", "studentAmount", "studyCountryInId", "addedQuarter",
            "hasFinanceRecord", "pendingRequestId", "pendingRequestedAt"};

    @Autowired private StudentFinanceController controller;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void removeRaisedRequests() {
        TenantContext.runAsSystem(() -> {
            List<Long> ids = jdbc.queryForList(
                    "SELECT Id FROM ALERTCORE.ApprovalRequests WHERE EntityType = 'STUDENT_FINANCE' AND RequestData = ?",
                    Long.class, FIXTURE_BODY);
            for (Long id : ids) {
                jdbc.update("DELETE FROM ALERTCORE.ApprovalSteps WHERE RequestId = ?", id);
                jdbc.update("DELETE FROM ALERTCORE.ApprovalHistory WHERE RequestId = ?", id);
                jdbc.update("DELETE FROM ALERTCORE.ApprovalRequests WHERE Id = ?", id);
                jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE TABLE_NAME = 'ApprovalRequests' AND RECORD_ID = ?",
                        String.valueOf(id));
            }
        });
    }

    /** An Egyptian student with no finance request open. */
    private Integer aStudent() {
        List<Integer> ids = jdbc.queryForList("""
                SELECT TOP 1 sd.STUDENT_ID FROM MOHECORE.STUDENT_DATA sd
                WHERE sd.STUDY_COUNTRY_IN IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM ALERTCORE.ApprovalRequests r
                                  WHERE r.EntityType = 'STUDENT_FINANCE'
                                    AND r.EntityId = CAST(sd.STUDENT_ID AS NVARCHAR(50))
                                    AND r.Status IN ('PENDING', 'IN_PROGRESS'))
                ORDER BY sd.STUDENT_ID
                """, Integer.class);
        Assumptions.assumeFalse(ids.isEmpty(), "every student has a finance request open");
        return ids.get(0);
    }

    private boolean hasFinanceRow(Integer studentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM MOHECORE.STUDENT_FINANCE WHERE STUDENT_ID = ?",
                Integer.class, studentId) > 0;
    }

    private long raise(Integer studentId, String status) {
        return TenantContext.runAsSystem(() -> jdbc.queryForObject("""
                INSERT INTO ALERTCORE.ApprovalRequests
                       (ConfigId, RequestType, EntityType, EntityId, RequestData, CurrentLevel, Status,
                        RequestedBy, RequestedAt, CountryId)
                VALUES ((SELECT TOP 1 Id FROM ALERTCORE.ApprovalConfigs WHERE EntityType = 'STUDENT_FINANCE'
                         ORDER BY IsActive DESC, Id),
                        'INSERT', 'STUDENT_FINANCE', ?, ?, 1, ?, ?, SYSUTCDATETIME(), 1);
                SELECT CAST(SCOPE_IDENTITY() AS BIGINT);
                """, Long.class, String.valueOf(studentId), FIXTURE_BODY, status, AsUser.SUPERADMIN));
    }

    private Map<String, Object> newRecordFor(long userId, List<String> authorities, Integer studentId) {
        return AsUser.callWithAuthorities(userId, authorities, () -> controller.newRecordFor(studentId));
    }

    @Test
    @DisplayName("the defaults are the student's own, and the answer holds those few fields and no more")
    void defaultsComeFromTheStudent() {
        Integer studentId = aStudent();
        Map<String, Object> onFile = jdbc.queryForMap("""
                SELECT STUDENT_NAME, STUDENT_AMOUNT, STUDY_COUNTRY_IN, STUDENT_ADDED_QUARTER
                FROM MOHECORE.STUDENT_DATA WHERE STUDENT_ID = ?""", studentId);

        Map<String, Object> answer = newRecordFor(AsUser.SUPERADMIN, FINANCE_READER, studentId);

        assertThat(answer).containsOnlyKeys(ANSWER_KEYS);
        assertThat(answer.get("studentId")).isEqualTo(studentId);
        assertThat(answer.get("studentName")).isEqualTo(onFile.get("STUDENT_NAME"));
        assertThat(answer.get("studentAmount")).isEqualTo(onFile.get("STUDENT_AMOUNT"));
        assertThat(answer.get("studyCountryInId")).isEqualTo(onFile.get("STUDY_COUNTRY_IN"));
        assertThat(answer.get("addedQuarter")).isEqualTo(onFile.get("STUDENT_ADDED_QUARTER"));
        assertThat(answer.get("hasFinanceRecord")).isEqualTo(hasFinanceRow(studentId));
        assertThat(answer.get("pendingRequestId")).isNull();
        assertThat(answer.get("pendingRequestedAt")).isNull();
    }

    @Test
    @DisplayName("a student who already has a finance record is flagged, so the form offers the edit instead")
    void existingRecordIsFlagged() {
        Integer studentId = aStudent();
        Assumptions.assumeTrue(hasFinanceRow(studentId), "the student picked has no finance record");

        assertThat(newRecordFor(AsUser.SUPERADMIN, FINANCE_READER, studentId).get("hasFinanceRecord")).isEqualTo(true);
    }

    @Test
    @DisplayName("a student with no finance record yet is told so, and nothing is pending for them")
    void noRecordYetIsFlaggedFalse() {
        // Every student on this database already has a finance record, and STUDENT_FINANCE
        // is not the test's to delete from, so this branch is read on a controller whose
        // three collaborators are stand-ins. The gate and the scope are held above.
        com.dilaw.mohe.model.StudentDatum student = new com.dilaw.mohe.model.StudentDatum();
        student.setStudentId(77);
        student.setStudentName("A new student");
        student.setStudentAmount(1500);
        student.setStudyCountryIn(1);
        student.setStudentAddedQuarter(20261);
        com.dilaw.mohe.service.StudentAccessGuard guard = mock(com.dilaw.mohe.service.StudentAccessGuard.class);
        when(guard.requireStudent(77)).thenReturn(student);
        com.dilaw.mohe.repository.StudentFinanceRepository finance = mock(com.dilaw.mohe.repository.StudentFinanceRepository.class);
        when(finance.existsById(77)).thenReturn(false);
        com.dilaw.mohe.approval.repository.ApprovalRequestRepository requests =
                mock(com.dilaw.mohe.approval.repository.ApprovalRequestRepository.class);
        StudentFinanceController standalone = new StudentFinanceController();
        ReflectionTestUtils.setField(standalone, "studentAccessGuard", guard);
        ReflectionTestUtils.setField(standalone, "financeRepository", finance);
        ReflectionTestUtils.setField(standalone, "approvalRequestRepository", requests);

        Map<String, Object> answer = standalone.newRecordFor(77);

        assertThat(answer).containsOnlyKeys(ANSWER_KEYS);
        assertThat(answer.get("hasFinanceRecord")).isEqualTo(false);
        assertThat(answer.get("studentAmount")).isEqualTo(1500);
        assertThat(answer.get("studyCountryInId")).isEqualTo(1);
        assertThat(answer.get("addedQuarter")).isEqualTo(20261);
        assertThat(answer.get("pendingRequestId")).isNull();
    }

    @Test
    @DisplayName("a request awaiting a decision is named, with when it was raised; PENDING and IN_PROGRESS both count")
    void pendingRequestIsNamed() {
        Integer studentId = aStudent();

        long pending = raise(studentId, "PENDING");
        Map<String, Object> answer = newRecordFor(AsUser.SUPERADMIN, FINANCE_READER, studentId);
        assertThat(answer.get("pendingRequestId")).isEqualTo(pending);
        assertThat(answer.get("pendingRequestedAt")).isNotNull();

        removeRaisedRequests();
        long inProgress = raise(studentId, "IN_PROGRESS");
        assertThat(newRecordFor(AsUser.SUPERADMIN, FINANCE_READER, studentId).get("pendingRequestId"))
                .isEqualTo(inProgress);
    }

    @Test
    @DisplayName("a decided request is not a reason to refuse: it is not reported")
    void decidedRequestIsNotReported() {
        Integer studentId = aStudent();
        raise(studentId, "REJECTED");
        raise(studentId, "EXECUTED");

        assertThat(newRecordFor(AsUser.SUPERADMIN, FINANCE_READER, studentId).get("pendingRequestId")).isNull();
    }

    @Test
    @DisplayName("behind the finance gate: the student and approvals permissions are not enough")
    void financeGated() {
        Integer studentId = aStudent();

        assertThatThrownBy(() -> newRecordFor(AsUser.SUPERADMIN,
                List.of("STUDENTS.VIEW", "STUDENTS.EDIT", "APPROVALS.VIEW"), studentId))
                .isInstanceOf(AccessDeniedException.class);
        for (String finance : List.of("FINANCE.VIEW", "FINANCE.EDIT", "FINANCE.EXPORT")) {
            assertThat(newRecordFor(AsUser.SUPERADMIN, List.of(finance), studentId))
                    .as("positive control: %s", finance).containsKey("hasFinanceRecord");
        }
    }

    @Test
    @DisplayName("a student outside the caller's countries is refused, and one that does not exist is 404")
    void scopedToTheCallersStudents() {
        Integer studentId = aStudent();

        // With the row-level policy in force the row is not even found for Jordan's
        // officer (404); the service's own country check (403) is the layer behind it.
        // Either way nothing of the student is answered.
        assertThatThrownBy(() -> newRecordFor(AsUser.JO_ADMIN, FINANCE_READER, studentId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
        assertThatThrownBy(() -> newRecordFor(AsUser.SUPERADMIN, FINANCE_READER, NO_SUCH_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
