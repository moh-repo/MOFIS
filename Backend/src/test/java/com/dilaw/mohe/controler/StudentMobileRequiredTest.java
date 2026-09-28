package com.dilaw.mohe.controler;

import com.dilaw.mohe.exception.LocalizedArgumentException;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.security.field.FieldAccessService;
import com.dilaw.mohe.security.policy.AccessControl;
import com.dilaw.mohe.service.StudentDataService;
import com.dilaw.mohe.util.PhoneNumbers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Live QA L9, decision D11: the Add Student wizard let a student be saved with no mobile
 * number, and no server rule required one. A mobile number is now required when a
 * student is added and when the student form saves an edit of one who has none yet.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentMobileRequiredTest {

    @Mock private AccessControl accessControl;
    @Mock private FieldAccessService fieldAccess;
    @Mock private StudentDataService studentDataService;
    @InjectMocks private StudentDataController controller;

    private static void assertMobileRequired(Throwable e) {
        assertThat(e).isInstanceOf(LocalizedArgumentException.class);
        assertThat(((LocalizedArgumentException) e).messageKey()).isEqualTo("validation.mobileRequired");
    }

    @Test
    @DisplayName("the rule: a blank mobile number is refused, a given one passes")
    void rule() {
        assertThatThrownBy(() -> PhoneNumbers.requireMobilePresent("  ")).satisfies(StudentMobileRequiredTest::assertMobileRequired);
        assertThatThrownBy(() -> PhoneNumbers.requireMobilePresent(null)).satisfies(StudentMobileRequiredTest::assertMobileRequired);
        assertThatCode(() -> PhoneNumbers.requireMobilePresent("+962 79 123 4567")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a new student without a mobile number is refused before anything is stored")
    void createRequiresIt() {
        assertThatThrownBy(() -> controller.saveStudentDatum(
                "{\"studentName\":\"QA\",\"studentEmail\":\"qa@example.org\"}",
                null, null, null, null, null, null, new MockHttpServletRequest()))
                .satisfies(StudentMobileRequiredTest::assertMobileRequired);
        verifyNoInteractions(studentDataService);
    }

    @Test
    @DisplayName("an edit of a student who has none must give one")
    void editCompletesIt() {
        StudentDatum stored = new StudentDatum();
        stored.setStudentId(7);
        stored.setStudentName("QA");
        when(studentDataService.getStudentData(7)).thenReturn(stored);

        assertThatThrownBy(() -> controller.updateStudentDatum(7, "{\"studentEmail\":\"new@example.org\"}",
                null, null, null, new MockHttpServletRequest()))
                .satisfies(StudentMobileRequiredTest::assertMobileRequired);
    }
}
