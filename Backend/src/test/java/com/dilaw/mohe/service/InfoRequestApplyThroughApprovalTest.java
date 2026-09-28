package com.dilaw.mohe.service;

import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.codes.InfoRequestStatus;
import com.dilaw.mohe.codes.InfoReviewStatus;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentInfoRequest;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.repository.StudentInfoRequestRepository;
import com.dilaw.mohe.repository.StudentInfoRequestStagingRepository;
import com.dilaw.mohe.security.field.FieldAccessService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * H3-b, decision D4-a (25 Sep 2026): "approve and apply" on an information request wrote
 * the student's answer straight into STUDENT_DATA / STUDENT_FINANCE, past the approval
 * chains and the field masks that every other edit goes through. It now raises an UPDATE
 * request in those chains, for the fields that change only, with the checks of a staff edit.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InfoRequestApplyThroughApprovalTest {

    private static final int STUDENT = 10117;
    private static final int REQUEST = 955;
    private static final long STAFF = 7L;

    @Mock private StudentInfoRequestRepository repository;
    @Mock private StudentInfoRequestStagingRepository stagingRepository;
    @Mock private StudentDataRepository studentDataRepository;
    @Mock private StudentDataService studentDataService;
    @Mock private ApprovalIntegrationService approvals;
    @Mock private StudentFinanceRepository financeRepository;
    @Mock private FieldAccessService fieldAccess;
    @Mock private StudentReferenceValidator references;
    @Mock private StudyFinReceivedStatusService receivedStatuses;
    @Mock private CountryScopeService countryScopeService;
    @Mock private StudentAccessGuard studentAccessGuard;
    @InjectMocks private StudentInfoRequestService service;

    private StudentInfoRequest request;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "jsonMapper", new ObjectMapper().findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
        ReflectionTestUtils.setField(service, "approvalIntegrationService", approvals);
        ReflectionTestUtils.setField(service, "studentFinanceRepository", financeRepository);
        request = new StudentInfoRequest();
        request.setId(REQUEST);
        request.setStudentId(STUDENT);
        request.setStatus(InfoRequestStatus.RESPONDED);
        when(repository.findById(REQUEST)).thenReturn(Optional.of(request));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        StudentDatum stored = new StudentDatum();
        stored.setStudentId(STUDENT);
        stored.setStudentEmail("old@example.org");
        when(studentDataService.getStudentData(STUDENT)).thenReturn(stored);
        when(receivedStatuses.paidStatusIds()).thenReturn(Set.of(1));
        signIn("STUDENT_INFO_REQUESTS.EDIT", "STUDENTS.EDIT");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(String... codes) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(String.valueOf(STAFF), null, codes));
    }

    private static ApprovalRequest raised(long id) {
        ApprovalRequest r = new ApprovalRequest();
        r.setId(id);
        return r;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> apply(Map<String, Object> body) {
        return (Map<String, Object>) service.approveAndApply(REQUEST, body, STAFF).orElseThrow();
    }

    @Test
    @DisplayName("a changed e-mail raises a STUDENT_DATA UPDATE request; nothing is written directly")
    void studentChangeGoesToTheChain() {
        when(approvals.createStudentDataRequest(eq(RequestType.UPDATE), any(), any(), eq(STAFF))).thenReturn(raised(501));

        Map<String, Object> outcome = apply(Map.of("student", Map.of("studentEmail", "new@example.org", "portalOnlyKey", 1)));

        ArgumentCaptor<StudentDatum> proposed = ArgumentCaptor.forClass(StudentDatum.class);
        ArgumentCaptor<StudentDatum> original = ArgumentCaptor.forClass(StudentDatum.class);
        verify(approvals).createStudentDataRequest(eq(RequestType.UPDATE), proposed.capture(), original.capture(), eq(STAFF));
        assertThat(proposed.getValue().getStudentEmail()).isEqualTo("new@example.org");
        assertThat(original.getValue().getStudentEmail()).isEqualTo("old@example.org");
        assertThat(outcome).containsEntry("studentApprovalRequestId", 501L).containsEntry("status", "SUBMITTED_FOR_APPROVAL");
        assertThat(request.getReviewStatus()).isEqualTo(InfoReviewStatus.APPROVED);
        verify(studentDataService, never()).update(anyInt(), any());
        verify(fieldAccess).assertMayEdit(eq(StudentDatum.class), any(), any(), eq(false));
    }

    @Test
    @DisplayName("an answer that changes nothing raises nothing; the request is still approved")
    void noChangeRaisesNothing() {
        Map<String, Object> outcome = apply(Map.of("student", Map.of("studentEmail", "old@example.org")));

        verifyNoInteractions(approvals);
        assertThat(outcome).containsEntry("status", "NO_CHANGES");
        assertThat(request.getReviewStatus()).isEqualTo(InfoReviewStatus.APPROVED);
    }

    @Test
    @DisplayName("a masked field the reviewer may not edit is refused and nothing is raised or saved")
    void fieldMasksApply() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "masked"))
                .when(fieldAccess).assertMayEdit(eq(StudentDatum.class), any(), any(), eq(false));

        assertThatThrownBy(() -> apply(Map.of("student", Map.of("studentEmail", "new@example.org"))))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(approvals);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a finance change raises a STUDENT_FINANCE UPDATE request, checked by the finance form's rules")
    void financeChangeGoesToTheChain() {
        signIn("STUDENT_INFO_REQUESTS.EDIT", "STUDENTS.EDIT", "FINANCE.EDIT");
        StudentFinance stored = new StudentFinance();
        stored.setId(STUDENT);
        stored.setStudentFinRecievedAmount(500);
        stored.setStudyFinRecievedStatus(99);
        stored.setStudyCountryInId(1);
        when(financeRepository.findById(STUDENT)).thenReturn(Optional.of(stored));
        when(approvals.createStudentFinanceRequest(eq(RequestType.UPDATE), any(), any(), eq(STAFF))).thenReturn(raised(502));

        Map<String, Object> outcome = apply(Map.of("finance", Map.of("studentFinRecievedAmount", 600)));

        assertThat(outcome).containsEntry("financeApprovalRequestId", 502L);
        assertThat(stored.getStudentFinRecievedAmount()).as("the managed row is not touched").isEqualTo(500);
    }

    @Test
    @DisplayName("a finance proposal for a student with no finance record is refused (409)")
    void noFinanceRecord() {
        signIn("STUDENT_INFO_REQUESTS.EDIT", "STUDENTS.EDIT", "FINANCE.EDIT");
        when(financeRepository.findById(STUDENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apply(Map.of("finance", Map.of("studentFinRecievedAmount", 600))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(approvals);
    }
}
