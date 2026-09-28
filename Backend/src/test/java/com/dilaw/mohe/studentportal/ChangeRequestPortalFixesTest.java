package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.service.StudentDocumentCountryMigrationService;
import com.dilaw.mohe.studentportal.repository.StudentSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * System test PRT-03 (an approved study-country change moves the files and ends the
 * sessions) and PRT-12 (a numeric or malformed {@code newValue} is read or refused with
 * a 400, never a 500).
 */
class ChangeRequestPortalFixesTest {

    @Test
    @DisplayName("PRT-03: a changed study country relocates the files and ends every portal session of the student")
    void countryChangeRelocatesAndEndsSessions() {
        StudentChangeRequestService service = new StudentChangeRequestService();
        StudentDocumentCountryMigrationService migration = mock(StudentDocumentCountryMigrationService.class);
        StudentSessionRepository sessions = mock(StudentSessionRepository.class);
        ReflectionTestUtils.setField(service, "documentCountryMigration", migration);
        ReflectionTestUtils.setField(service, "sessionRepository", sessions);

        service.followCountryChange(42, 1004, 1007);

        verify(migration).relocateForCountryChange(42, 1004, 1007);
        verify(sessions).deleteByStudentId(42);
    }

    @Test
    @DisplayName("PRT-03: any other approved field leaves files and sessions alone")
    void sameCountryDoesNothing() {
        StudentChangeRequestService service = new StudentChangeRequestService();
        StudentDocumentCountryMigrationService migration = mock(StudentDocumentCountryMigrationService.class);
        StudentSessionRepository sessions = mock(StudentSessionRepository.class);
        ReflectionTestUtils.setField(service, "documentCountryMigration", migration);
        ReflectionTestUtils.setField(service, "sessionRepository", sessions);

        service.followCountryChange(42, 1004, 1004);

        verifyNoInteractions(migration, sessions);
    }

    @Test
    @DisplayName("PRT-12: numbers and booleans are read as text; objects, lists and non-object entries are refused")
    void shapes() {
        List<StudentChangeRequestService.FieldChangeInput> read = StudentChangeRequestController.toChanges(List.of(
                Map.of("fieldName", "studyUniversityIn", "newValue", 17),
                Map.of("fieldName", "studentName", "fieldLabel", "Name", "newValue", "Ahmed")));
        assertThat(read).extracting(StudentChangeRequestService.FieldChangeInput::newValue).containsExactly("17", "Ahmed");

        assertThat(StudentChangeRequestController.toChanges(List.of(Map.of("fieldName", "studentName", "newValue", Map.of("x", 1)))))
                .isNull();
        assertThat(StudentChangeRequestController.toChanges(List.of(Map.of("fieldName", "studentName", "newValue", List.of("a")))))
                .isNull();
        assertThat(StudentChangeRequestController.toChanges(List.of("studentName"))).isNull();
    }

    @Test
    @DisplayName("PRT-12: through the endpoint, a numeric value reaches the service as text and an object value is a 400")
    void throughTheEndpoint() {
        StudentChangeRequestController controller = new StudentChangeRequestController();
        StudentChangeRequestService service = mock(StudentChangeRequestService.class);
        MessageSource messages = mock(MessageSource.class);
        when(messages.getMessage(any(String.class), any(), any())).thenAnswer(inv -> inv.getArgument(0));
        ReflectionTestUtils.setField(controller, "changeRequestService", service);
        ReflectionTestUtils.setField(controller, "messageSource", messages);
        com.dilaw.mohe.studentportal.model.StudentChangeRequest created = new com.dilaw.mohe.studentportal.model.StudentChangeRequest();
        created.setSubmissionId("abc");
        when(service.submitRequest(anyInt(), anyInt(), any())).thenReturn(List.of(created));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(StudentPortalTokenInterceptor.ATTR_STUDENT_ID, 5);
        request.setAttribute(StudentPortalTokenInterceptor.ATTR_STUDENT_USER_ID, 6);

        ResponseEntity<?> ok = controller.submit(Map.<String, Object>of("changes",
                List.of(Map.of("fieldName", "studentCurrentLevelId", "newValue", 3))), request);
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        verify(service).submitRequest(eq(5), eq(6),
                eq(List.of(new StudentChangeRequestService.FieldChangeInput("studentCurrentLevelId", null, "3"))));

        ResponseEntity<?> bad = controller.submit(Map.<String, Object>of("changes",
                List.of(Map.of("fieldName", "studentName", "newValue", Map.of("first", "A")))), request);
        assertThat(bad.getStatusCode().value()).isEqualTo(400);
        assertThat(bad.getBody()).isEqualTo(Map.of("error", "portal.changeRequest.badShape"));
    }
}
