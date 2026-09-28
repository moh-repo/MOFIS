package com.dilaw.mohe.accounting;

import com.dilaw.mohe.accounting.model.AttacheAllocation;
import com.dilaw.mohe.accounting.repository.AttacheAllocationRepository;
import com.dilaw.mohe.accounting.service.AllocationService;
import com.dilaw.mohe.accounting.service.AttacheCountryValidator;
import com.dilaw.mohe.accounting.service.FiscalPeriodService;
import com.dilaw.mohe.model.StudyCountryIn;
import com.dilaw.mohe.repository.StudyCountryInRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Live QA H6 (25 Sep 2026): the accounting screens offered home countries
 * (STUDENT_COUNTRY) as the attaché, and that table reuses the study-country ids
 * for other countries, so a pick was stored under a different attaché. The
 * server now refuses an id that is not an active STUDY_COUNTRY_IN row.
 */
class AttacheCountryTest {

    private static final int EGYPT = 1;
    private static final int RETIRED = 1010;
    private static final int HOME_ONLY = 4242;

    private StudyCountryInRepository studyCountries;
    private AttacheCountryValidator validator;

    @BeforeEach
    void setUp() {
        studyCountries = Mockito.mock(StudyCountryInRepository.class);
        when(studyCountries.findById(EGYPT)).thenReturn(Optional.of(country(true)));
        when(studyCountries.findById(RETIRED)).thenReturn(Optional.of(country(false)));
        when(studyCountries.findById(HOME_ONLY)).thenReturn(Optional.empty());
        validator = new AttacheCountryValidator();
        ReflectionTestUtils.setField(validator, "studyCountries", studyCountries);
    }

    private static StudyCountryIn country(boolean active) {
        StudyCountryIn c = new StudyCountryIn();
        c.setIsActive(active);
        return c;
    }

    private static void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("an active study country passes; none (ministry-wide) is the caller's rule")
    void activeStudyCountryPasses() {
        assertThatCode(() -> validator.requireActive(EGYPT)).doesNotThrowAnyException();
        assertThatCode(() -> validator.requireActive(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an id with no study-country row, or a retired one, is refused")
    void unknownOrRetiredRefused() {
        assertBadRequest(() -> validator.requireActive(HOME_ONLY));
        assertBadRequest(() -> validator.requireActive(RETIRED));
    }

    @Test
    @DisplayName("an allocation to a non-study country is refused before anything is saved")
    void allocationRefusedBeforeSave() {
        AttacheAllocationRepository allocations = Mockito.mock(AttacheAllocationRepository.class);
        CountryScopeService scope = Mockito.mock(CountryScopeService.class);
        when(scope.canAccessCountry(any())).thenReturn(true);
        AllocationService service = new AllocationService();
        ReflectionTestUtils.setField(service, "allocationRepository", allocations);
        ReflectionTestUtils.setField(service, "countryScopeService", scope);
        ReflectionTestUtils.setField(service, "fiscalPeriodService", Mockito.mock(FiscalPeriodService.class));
        ReflectionTestUtils.setField(service, "attacheCountries", validator);

        AttacheAllocation draft = new AttacheAllocation();
        draft.setStudyCountryInId(HOME_ONLY);
        draft.setMoheQuarterKey(20253);
        draft.setFiscalYearId(2);
        draft.setAmount(new BigDecimal("1000"));
        draft.setAllocationType(AttacheAllocation.REGULAR);

        assertBadRequest(() -> service.create(draft));
        verify(allocations, never()).save(any());
    }
}
