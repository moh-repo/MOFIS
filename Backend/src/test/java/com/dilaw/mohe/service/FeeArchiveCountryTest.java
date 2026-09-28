package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyUniversityFeeArch;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudyUniversityFeeArchRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fee History "Add" failed for every ministry user (live QA #8, G2).
 *
 * <p>The form sends no country: the row is for the student picked above it. The
 * service fell back to the caller's own country only when they had exactly one,
 * which a ministry user never has, so the answer was 400 "the attaché this fee
 * belongs to is required". The student's attaché is what the form means.
 */
class FeeArchiveCountryTest {

    private final StudyUniversityFeeArchRepository repository = mock(StudyUniversityFeeArchRepository.class);
    private final CountryScopeService scope = mock(CountryScopeService.class);
    private final StudentDataRepository students = mock(StudentDataRepository.class);
    private final StudyUniversityFeeArchService service = new StudyUniversityFeeArchService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "countryScopeService", scope);
        ReflectionTestUtils.setField(service, "studentDataRepository", students);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static StudyUniversityFeeArch rowFor(int studentId) {
        StudyUniversityFeeArch row = new StudyUniversityFeeArch();
        row.setStudentId(studentId);
        return row;
    }

    @Test
    @DisplayName("a ministry user's row takes the student's attaché")
    void ministryUserGetsTheStudentsCountry() {
        when(scope.getScope()).thenReturn(new CountryScope(1L, true, List.of()));
        when(students.findStudyCountryInByStudentId(4)).thenReturn(Optional.of(1));

        StudyUniversityFeeArch saved = service.create(rowFor(4));

        assertThat(saved.getStudyCountryInId()).isEqualTo(1);
    }

    @Test
    @DisplayName("the student's attaché is still checked against the caller's scope")
    void theStudentsCountryIsStillChecked() {
        when(scope.getScope()).thenReturn(new CountryScope(13L, false, List.of(1004)));
        when(students.findStudyCountryInByStudentId(4)).thenReturn(Optional.of(1));
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
                .when(scope).validateCountryAccess(1);

        assertThatThrownBy(() -> service.create(rowFor(4))).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("without a student country, a single-country user still falls back to their own")
    void singleCountryFallbackIsKept() {
        when(scope.getScope()).thenReturn(new CountryScope(13L, false, List.of(1004)));
        when(students.findStudyCountryInByStudentId(4)).thenReturn(Optional.empty());

        assertThat(service.create(rowFor(4)).getStudyCountryInId()).isEqualTo(1004);
    }
}
