package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyUniversityFee;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudyUniversityFeeRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * University fee rows (QA B13, FIN-005).
 *
 * <p>The discount was checked only by the form, so a request straight to the API
 * could store a discount above the fee, which the net-amount rule then clamped to
 * 0 without a word. And the row's copy of the student's name came from the form,
 * so rows of one student could carry two spellings and read as two students.
 */
class UniversityFeeDiscountAndNameTest {

    private final StudyUniversityFeeRepository repository = mock(StudyUniversityFeeRepository.class);
    private final StudentDataRepository students = mock(StudentDataRepository.class);
    private final CountryScopeService scope = mock(CountryScopeService.class);
    private final StudyUniversityFeeService service = new StudyUniversityFeeService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "studentDataRepository", students);
        ReflectionTestUtils.setField(service, "countryScopeService", scope);
        ReflectionTestUtils.setField(service, "studentFinanceWafarService", mock(StudentFinanceWafarService.class));
        // The student is one the caller may reach (N14 moved that check to the guard).
        ReflectionTestUtils.setField(service, "studentAccessGuard", mock(StudentAccessGuard.class));
        when(scope.canAccessCountry(anyInt())).thenReturn(true);
        when(students.findStudyCountryInByStudentId(4)).thenReturn(Optional.of(1));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static StudyUniversityFee fee(int amount, int discount) {
        StudyUniversityFee f = new StudyUniversityFee();
        f.setStudentId(4);
        f.setStudyUniversityFeeStudentName("typed in the form");
        f.setStudyUniversityFeeYear("2023/2024");
        f.setStudyUniversityFeeSemesterNumber(1);
        f.setStudyUniversityFeeYearScopeId(1);
        f.setStudyUniversityFeeSemesterScopeId(1);
        f.setStudyUniversityFeeSourceId(1);
        f.setStudyUniversityCheckPayorTypeId(1);
        f.setStudyUniversityFeeAmount(amount);
        f.setStudyUniversityFeeDiscount(discount);
        return f;
    }

    @Test
    @DisplayName("a discount above the fee is refused, not clamped")
    void discountAboveTheFeeIsRefused() {
        assertThatThrownBy(() -> service.create(fee(1000, 1500)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("discount");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a negative discount is refused")
    void negativeDiscountIsRefused() {
        assertThatThrownBy(() -> service.create(fee(1000, -1))).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a discount of the whole fee is allowed, and nets to 0")
    void wholeFeeDiscountIsAllowed() {
        when(students.findStudentNameById(4)).thenReturn(Optional.of("Layla Al Mansouri"));

        StudyUniversityFee saved = service.create(fee(1000, 1000));

        assertThat(saved.getStudyUniversityRealFeeAmount()).isZero();
    }

    @Test
    @DisplayName("the row carries the student's name on file, whatever the form sent")
    void nameComesFromTheStudent() {
        when(students.findStudentNameById(4)).thenReturn(Optional.of("Layla Al Mansouri"));

        assertThat(service.create(fee(1000, 0)).getStudyUniversityFeeStudentName()).isEqualTo("Layla Al Mansouri");
    }

    @Test
    @DisplayName("without a name on file, the form's is kept rather than blanked")
    void formNameKeptWhenNoneOnFile() {
        when(students.findStudentNameById(4)).thenReturn(Optional.empty());

        assertThat(service.create(fee(1000, 0)).getStudyUniversityFeeStudentName()).isEqualTo("typed in the form");
    }
}
