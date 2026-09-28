package com.dilaw.mohe.controler;

import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.security.field.FieldAccessService;
import com.dilaw.mohe.security.policy.AccessControl;
import com.dilaw.mohe.service.PendingOnboardingUploadService;
import com.dilaw.mohe.service.StudentDataService;
import com.dilaw.mohe.service.StudentFamilyService;
import com.dilaw.mohe.service.StudentReferenceValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-5 QA R5-27 (long-standing): the reply to "add student" promised "a separate
 * finance approval will be created after the student is approved". None is ever raised;
 * the finance row is written with the student when the request is approved. Decision
 * D-8: the reply says so.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentCreateReplyTest {

    @Mock private AccessControl accessControl;
    @Mock private FieldAccessService fieldAccess;
    @Mock private StudentDataService studentDataService;
    @Mock private StudentReferenceValidator studentReferenceValidator;
    @Mock private StudentFamilyService studentFamilyService;
    @Mock private PendingOnboardingUploadService pendingOnboardingUploadService;
    @Mock private ApprovalIntegrationService approvalIntegrationService;
    @InjectMocks private StudentDataController controller;

    @Test
    @DisplayName("the reply says the finance record comes with the approval, and promises no second request")
    void replyMatchesWhatHappens() {
        ResponseEntity<?> reply = AsUser.call(13L, () -> controller.saveStudentDatum(
                "{\"studentName\":\"QA\",\"studentCellphone\":\"+962791234567\",\"studyCountryIn\":1004}",
                null, null, null, null, null, null, new MockHttpServletRequest()));

        assertThat(reply.getStatusCode().is2xxSuccessful()).as("%s", reply.getBody()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) reply.getBody();
        assertThat(body).doesNotContainKey("linkedFinanceWillFollow");
        assertThat((String) body.get("message"))
                .contains("finance record is created when the request is approved")
                .doesNotContain("separate finance approval");
        assertThat(body.get("status")).isEqualTo("PENDING");
    }
}
