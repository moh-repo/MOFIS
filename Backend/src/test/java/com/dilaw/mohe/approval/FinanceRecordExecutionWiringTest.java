package com.dilaw.mohe.approval;

import com.dilaw.mohe.approval.event.ApprovalExecutionEvent;
import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.model.StudentBooksAllowance;
import com.dilaw.mohe.model.StudentLoan;
import com.dilaw.mohe.model.StudyFinReceivedWhom;
import com.dilaw.mohe.service.StudentBooksAllowanceService;
import com.dilaw.mohe.service.StudentLoanService;
import com.dilaw.mohe.service.StudyFinReceivedWhomService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The three finance screens reach their tables through the executor and nowhere
 * else now, so the wiring in {@code handleApprovalExecution} is the whole
 * feature: a case that dispatches to the wrong method, or to none, leaves an
 * approved request that changed nothing and says so nowhere.
 *
 * <p>The rules those service methods carry — the identity id cleared, MIDATE
 * stamped, the country resolved, an edit that keeps the columns it did not name
 * — are held against the real database by the three CRUD tests. What cannot be
 * seen from there is the executor simply not calling them, which is what this
 * pins down. Written in the shape of {@code ApprovalWafarSyncWiringTest}, for
 * the same reason it exists.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinanceRecordExecutionWiringTest {

    @Mock private StudentLoanService studentLoanService;
    @Mock private StudentBooksAllowanceService studentBooksAllowanceService;
    @Mock private StudyFinReceivedWhomService studyFinReceivedWhomService;
    /** A recipient delete now looks for pending finance links to withdraw (live QA L8); none here. */
    @Mock private com.dilaw.mohe.approval.service.ApprovalWorkflowService workflowService;

    @InjectMocks private ApprovalIntegrationService service;

    private final ObjectMapper json = new ObjectMapper();

    private ApprovalRequest request(String entityType, RequestType type, String entityId, String payload) {
        ApprovalRequest r = new ApprovalRequest();
        r.setId(1L);
        r.setEntityType(entityType);
        r.setRequestType(type);
        r.setEntityId(entityId);
        r.setRequestData(payload);
        return r;
    }

    private void execute(ApprovalRequest r) {
        service.handleApprovalExecution(new ApprovalExecutionEvent(this, r, 1L));
    }

    // ── Loans ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an approved loan insert is created, not merged onto an id from the payload")
    void loanInsert() {
        when(studentLoanService.create(any())).thenReturn(new StudentLoan());

        execute(request("STUDENT_LOAN", RequestType.INSERT, null,
                "{\"studentId\":11,\"loanAmount\":4500}"));

        ArgumentCaptor<StudentLoan> saved = ArgumentCaptor.forClass(StudentLoan.class);
        verify(studentLoanService).create(saved.capture());
        assertThat(saved.getValue().getStudentId()).isEqualTo(11);
        assertThat(saved.getValue().getLoanAmount()).isEqualTo(4500);
        verify(studentLoanService, never()).update(any(), any());
    }

    @Test
    @DisplayName("an approved loan edit replays the dialog's own tree, not a bound entity")
    void loanUpdate() {
        execute(request("STUDENT_LOAN", RequestType.UPDATE, "7", "{\"loanStatus\":2}"));

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(studentLoanService).update(eq(7), body.capture());
        // The tree is what makes an omitted column different from a cleared one.
        assertThat(body.getValue().has("loanStatus")).isTrue();
        assertThat(body.getValue().has("repaymentNotes")).as("never sent, never replayed").isFalse();
    }

    @Test
    @DisplayName("an approved loan delete removes the row it names")
    void loanDelete() {
        execute(request("STUDENT_LOAN", RequestType.DELETE, "7", "{}"));
        verify(studentLoanService).delete(7);
    }

    // ── Books allowances ─────────────────────────────────────────────────────

    @Test
    @DisplayName("an approved books allowance insert reaches its service")
    void booksInsert() {
        when(studentBooksAllowanceService.create(any())).thenReturn(new StudentBooksAllowance());

        execute(request("STUDENT_BOOKS_ALLOWANCE", RequestType.INSERT, null,
                "{\"studentId\":12,\"allowanceAmount\":375}"));

        ArgumentCaptor<StudentBooksAllowance> saved = ArgumentCaptor.forClass(StudentBooksAllowance.class);
        verify(studentBooksAllowanceService).create(saved.capture());
        assertThat(saved.getValue().getStudentId()).isEqualTo(12);
        assertThat(saved.getValue().getAllowanceAmount()).isEqualTo(375);
    }

    @Test
    @DisplayName("an approved books allowance edit and delete reach their service")
    void booksUpdateAndDelete() {
        execute(request("STUDENT_BOOKS_ALLOWANCE", RequestType.UPDATE, "3", "{\"payStatus\":1}"));
        execute(request("STUDENT_BOOKS_ALLOWANCE", RequestType.DELETE, "3", "{}"));

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(studentBooksAllowanceService).update(eq(3), body.capture());
        assertThat(body.getValue().path("payStatus").asInt()).isEqualTo(1);
        verify(studentBooksAllowanceService).delete(3);
    }

    // ── Finance recipients ───────────────────────────────────────────────────

    @Test
    @DisplayName("an approved recipient insert reaches its service")
    void recipientInsert() {
        when(studyFinReceivedWhomService.create(any())).thenReturn(new StudyFinReceivedWhom());

        execute(request("STUDY_FIN_RECEIVED_WHOM", RequestType.INSERT, null,
                "{\"finReceivedName\":\"Ahmed\",\"financeCountryId\":1}"));

        ArgumentCaptor<StudyFinReceivedWhom> saved = ArgumentCaptor.forClass(StudyFinReceivedWhom.class);
        verify(studyFinReceivedWhomService).create(saved.capture());
        assertThat(saved.getValue().getFinReceivedName()).isEqualTo("Ahmed");
    }

    @Test
    @DisplayName("an approved recipient edit and delete reach their service")
    void recipientUpdateAndDelete() {
        when(workflowService.getPendingByEntityType("STUDENT_FINANCE")).thenReturn(java.util.List.of());

        execute(request("STUDY_FIN_RECEIVED_WHOM", RequestType.UPDATE, "5", "{\"finRecievedAmount\":1500}"));
        execute(request("STUDY_FIN_RECEIVED_WHOM", RequestType.DELETE, "5", "{}"));

        // The delete asked the queue for finance links to the recipient (live QA L8).
        verify(workflowService).getPendingByEntityType("STUDENT_FINANCE");

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(studyFinReceivedWhomService).update(eq(5), body.capture());
        assertThat(body.getValue().path("finRecievedAmount").asInt()).isEqualTo(1500);
        // The encrypted document folder goes with the row; that cleanup moved into
        // the service when the controller stopped deleting anything itself.
        verify(studyFinReceivedWhomService).delete(5);
    }

    @Test
    @DisplayName("an unknown entity type touches none of the three")
    void unknownTypeIsInert() {
        execute(request("SOMETHING_ELSE", RequestType.DELETE, "1", "{}"));

        verify(studentLoanService, never()).delete(any());
        verify(studentBooksAllowanceService, never()).delete(any());
        verify(studyFinReceivedWhomService, never()).delete(any());
    }
}
