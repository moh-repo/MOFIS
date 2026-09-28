package com.dilaw.mohe.service;

import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.dto.StudentQuickEditDto;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.security.field.FieldAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Live QA L9 / D11, confirmed 26 Sep 2026 (FIX-PLAN P5-9): the student form requires a
 * mobile number (on create, and on an edit of a student who has none,
 * {@code StudentMobileRequiredTest}); **Quick Edit does not**. None of the 981 live
 * students on dev has one, and Quick Edit must still send their other changes for
 * approval. A number that is typed is still checked for its shape.
 */
class QuickEditMobileOptionalTest {

    private static final int STUDENT = 12;

    private final StudentAccessGuard guard = mock(StudentAccessGuard.class);
    private final ApprovalIntegrationService approvals = mock(ApprovalIntegrationService.class);
    private final StudentQuickEditService service = new StudentQuickEditService();

    QuickEditMobileOptionalTest() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ReflectionTestUtils.setField(service, "studentAccessGuard", guard);
        ReflectionTestUtils.setField(service, "approvalIntegrationService", approvals);
        ReflectionTestUtils.setField(service, "objectMapper", mapper);
        ReflectionTestUtils.setField(service, "fieldAccess", mock(FieldAccessService.class));
        ReflectionTestUtils.setField(service, "studentReferenceValidator", mock(StudentReferenceValidator.class));
        StudentFinanceRepository finance = mock(StudentFinanceRepository.class);
        when(finance.findById(STUDENT)).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(service, "studentFinanceRepository", finance);

        StudentDatum onFile = new StudentDatum();
        onFile.setStudentId(STUDENT);
        onFile.setStudentName("Old name");
        onFile.setStudyCountryIn(1004);
        when(guard.requireStudent(STUDENT)).thenReturn(onFile);
    }

    @AfterEach
    void signOut() {
        AsUser.signOut();
    }

    @Test
    @DisplayName("a student with no mobile number: another change is sent for approval, no mobile asked for")
    void noMobileIsFine() {
        AsUser.signIn(AsUser.JO_ADMIN);
        StudentQuickEditDto dto = new StudentQuickEditDto();
        dto.setStudentName("New name");
        dto.setStudentCellphone("");

        Map<String, Object> answer = service.update(STUDENT, dto);

        assertThat(answer.get("status")).isEqualTo("PENDING");
        verify(approvals).createStudentDataRequest(any(), any(), any(), eq(AsUser.JO_ADMIN));
    }

    @Test
    @DisplayName("a mobile number that is typed is still checked for its shape")
    void typedNumberIsChecked() {
        AsUser.signIn(AsUser.JO_ADMIN);
        StudentQuickEditDto dto = new StudentQuickEditDto();
        dto.setStudentName("Old name");
        dto.setStudentCellphone("12");

        assertThatThrownBy(() -> service.update(STUDENT, dto)).isInstanceOf(IllegalArgumentException.class);
    }
}
