package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.InfoRequestStatus;
import com.dilaw.mohe.model.StudentInfoRequest;
import com.dilaw.mohe.model.StudentInfoRequestStaging;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentInfoRequestRepository;
import com.dilaw.mohe.repository.StudentInfoRequestStagingRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Live QA H1/H2 (25 Sep 2026): information requests and their staging data are
 * reached only for students in the caller's scope.
 *
 * <p>Jordan and Saudi staff got 404 for an Egypt student's request (row-level
 * security hides the row) but 200 for its staging data, whose table has no
 * country column; and a Jordan account raised a request for an Egypt student.
 * Every path now goes through {@link StudentAccessGuard}; here the guard is a
 * mock that refuses the "other country" student, as the real one does.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentInfoRequestScopeTest {

    private static final int OWN_STUDENT = 10117;
    private static final int OTHER_COUNTRY_STUDENT = 2;
    private static final int REQUEST = 955;

    @Mock private StudentInfoRequestRepository repository;
    @Mock private StudentInfoRequestStagingRepository stagingRepository;
    @Mock private StudentDataRepository studentDataRepository;
    @Mock private StudentDataService studentDataService;
    @Mock private com.dilaw.mohe.approval.service.ApprovalIntegrationService approvalIntegrationService;
    @Mock private com.dilaw.mohe.repository.StudentFinanceRepository studentFinanceRepository;
    @Mock private com.dilaw.mohe.security.field.FieldAccessService fieldAccess;
    @Mock private StudentReferenceValidator studentReferenceValidator;
    @Mock private StudyFinReceivedStatusService receivedStatuses;
    @Mock private CountryScopeService countryScopeService;
    @Mock private StudentAccessGuard studentAccessGuard;
    @InjectMocks private StudentInfoRequestService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void guardRefusesOtherCountry() {
        when(studentAccessGuard.requireStudent(OTHER_COUNTRY_STUDENT))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: country not in scope"));
    }

    private StudentInfoRequest request(int studentId, String status) {
        StudentInfoRequest r = new StudentInfoRequest();
        r.setId(REQUEST);
        r.setStudentId(studentId);
        r.setStatus(status);
        when(repository.findById(REQUEST)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    @DisplayName("H1: staging of another country's request is refused and never read")
    void stagingOfAnotherCountryIsRefused() {
        guardRefusesOtherCountry();
        request(OTHER_COUNTRY_STUDENT, InfoRequestStatus.RESPONDED);

        assertThatThrownBy(() -> service.getStaging(REQUEST))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(stagingRepository);
    }

    @Test
    @DisplayName("H1: staging of a request hidden by row-level security is not found")
    void stagingOfAHiddenRequestIsNotFound() {
        when(repository.findById(REQUEST)).thenReturn(Optional.empty());

        assertThat(service.getStaging(REQUEST)).isEmpty();
        verifyNoInteractions(stagingRepository);
    }

    @Test
    @DisplayName("H1: staging of an own-country request is returned")
    void stagingOfAnOwnCountryRequestIsReturned() {
        request(OWN_STUDENT, InfoRequestStatus.RESPONDED);
        StudentInfoRequestStaging staging = new StudentInfoRequestStaging();
        staging.setRequestId(REQUEST);
        when(stagingRepository.findById(REQUEST)).thenReturn(Optional.of(staging));

        assertThat(service.getStaging(REQUEST)).contains(staging);
        verify(studentAccessGuard).requireStudent(OWN_STUDENT);
    }

    @Test
    @DisplayName("H2: an explicit target outside the scope refuses the whole request, before anything is saved")
    void explicitTargetOutsideScopeIsRefused() {
        guardRefusesOtherCountry();

        assertThatThrownBy(() -> service.resolveTargetStudentIds(
                List.of(OWN_STUDENT, OTHER_COUNTRY_STUDENT), false, null, null))
                .isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("H2: explicit targets inside the scope pass, once each")
    void explicitTargetsInsideScopePass() {
        assertThat(service.resolveTargetStudentIds(List.of(OWN_STUDENT, OWN_STUDENT), false, null, null))
                .containsExactly(OWN_STUDENT);
        verify(studentAccessGuard).requireStudent(OWN_STUDENT);
    }

    @Test
    @DisplayName("H2: \"all students\" for a country account is limited to its study countries")
    void allStudentsForACountryAccountIsScoped() {
        when(countryScopeService.getScope()).thenReturn(new CountryScope(13L, false, List.of(1004)));
        when(studentDataRepository.findStudentIdsByOptionalFiltersInStudyCountries(null, null, List.of(1004)))
                .thenReturn(List.of(OWN_STUDENT));

        assertThat(service.resolveTargetStudentIds(null, true, null, null)).containsExactly(OWN_STUDENT);
        verify(studentDataRepository, never()).findStudentIdsByOptionalFilters(any(), any());
    }

    @Test
    @DisplayName("H2: \"all students\" for an account with no country selects nobody")
    void allStudentsWithNoCountryIsEmpty() {
        when(countryScopeService.getScope()).thenReturn(new CountryScope(13L, false, List.of()));

        assertThat(service.resolveTargetStudentIds(null, true, null, null)).isEmpty();
        verify(studentDataRepository, never()).findStudentIdsByOptionalFilters(any(), any());
        verify(studentDataRepository, never()).findStudentIdsByOptionalFiltersInStudyCountries(any(), any(), anyList());
    }

    @Test
    @DisplayName("P5-4: \"all students in country X\" means the study country, in both queries")
    void allStudentsFilterOnTheStudyCountry() throws Exception {
        for (String name : List.of("findStudentIdsByOptionalFilters", "findStudentIdsByOptionalFiltersInStudyCountries")) {
            String jpql = java.util.Arrays.stream(StudentDataRepository.class.getMethods())
                    .filter(m -> m.getName().equals(name)).findFirst().orElseThrow()
                    .getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
            assertThat(jpql).as(name).contains("s.studyCountryIn = :countryId").doesNotContain("studentCountryId");
        }
    }

    @Test
    @DisplayName("P5-4: a country account naming another study country is refused, and nothing is selected")
    void allStudentsInAForeignStudyCountryIsRefused() {
        when(countryScopeService.getScope()).thenReturn(new CountryScope(13L, false, List.of(1004)));

        assertThatThrownBy(() -> service.resolveTargetStudentIds(null, true, 1, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(studentDataRepository, never()).findStudentIdsByOptionalFiltersInStudyCountries(any(), any(), anyList());

        when(studentDataRepository.findStudentIdsByOptionalFiltersInStudyCountries(1004, null, List.of(1004)))
                .thenReturn(List.of(OWN_STUDENT));
        assertThat(service.resolveTargetStudentIds(null, true, 1004, null)).containsExactly(OWN_STUDENT);
    }

    @Test
    @DisplayName("H2: \"all students\" for a ministry account is unrestricted")
    void allStudentsForAMinistryAccount() {
        when(countryScopeService.getScope()).thenReturn(new CountryScope(1L, true, List.of()));
        when(studentDataRepository.findStudentIdsByOptionalFilters(null, 1)).thenReturn(List.of(1, 2, 3));

        assertThat(service.resolveTargetStudentIds(null, true, null, 1)).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("H2: cancel, reject and approve of another country's request are refused and not saved")
    void decisionsOnAnotherCountryAreRefused() {
        guardRefusesOtherCountry();
        request(OTHER_COUNTRY_STUDENT, InfoRequestStatus.RESPONDED);

        assertThatThrownBy(() -> service.cancel(REQUEST, 7L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.reject(REQUEST, "no", 7L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.approve(REQUEST, 7L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.approveAndApply(REQUEST, Map.of(), 7L)).isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).save(any());
        verify(studentDataService, never()).update(anyInt(), any());
        verifyNoInteractions(approvalIntegrationService);
    }

    @Test
    @DisplayName("approve accepts only an answered request")
    void approveNeedsAnAnswer() {
        request(OWN_STUDENT, InfoRequestStatus.PENDING);

        assertThatThrownBy(() -> service.approve(REQUEST, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("H3: applying finance data needs FINANCE.EDIT")
    void applyingFinanceNeedsFinanceEdit() {
        request(OWN_STUDENT, InfoRequestStatus.RESPONDED);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("7", null, "STUDENT_INFO_REQUESTS.EDIT", "STUDENTS.EDIT"));

        assertThatThrownBy(() -> service.approveAndApply(REQUEST, Map.of("finance", Map.of("wafarAmount", 1)), 7L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(approvalIntegrationService);
        verify(studentDataService, never()).update(anyInt(), any());
    }

    @Test
    @DisplayName("H3: student data alone goes ahead without FINANCE.EDIT (as an approval request since H3-b)")
    void applyingStudentDataAlone() {
        request(OWN_STUDENT, InfoRequestStatus.RESPONDED);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        com.dilaw.mohe.model.StudentDatum stored = new com.dilaw.mohe.model.StudentDatum();
        stored.setStudentId(OWN_STUDENT);
        stored.setStudentEmail("old@b.c");
        when(studentDataService.getStudentData(OWN_STUDENT)).thenReturn(stored);
        com.dilaw.mohe.approval.model.ApprovalRequest raised = new com.dilaw.mohe.approval.model.ApprovalRequest();
        raised.setId(99L);
        when(approvalIntegrationService.createStudentDataRequest(any(), any(), any(), any(Long.class))).thenReturn(raised);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "jsonMapper",
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("7", null, "STUDENT_INFO_REQUESTS.EDIT", "STUDENTS.EDIT"));

        assertThat(service.approveAndApply(REQUEST, Map.of("student", Map.of("studentEmail", "a@b.c")), 7L)).isPresent();
        verify(studentDataService, never()).update(anyInt(), any());
    }
}
