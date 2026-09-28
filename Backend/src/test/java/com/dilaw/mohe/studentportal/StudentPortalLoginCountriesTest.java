package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.model.StudyCountryIn;
import com.dilaw.mohe.service.LookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The country selector on the portal login page had nothing in it, so the field —
 * which is required — could not be filled and no student could sign in.
 *
 * <p>The cause was not authorization. The page called
 * {@code /api/v1/lookup/study-countries-from-student-account}, which is
 * {@code permitAll}, and the request arrived and returned 200. What emptied it was
 * Row-Level Security: the list is read from {@code MOHECORE.STUDENT_DATA}, which
 * carries a filter predicate on {@code STUDY_COUNTRY_IN}, and an anonymous caller
 * has no scope for that predicate to match.
 *
 * <p>{@code LookupService} wraps the read in {@code runAsSystem} and that does not
 * help: the class is {@code @Transactional}, so the transaction opens — and
 * {@code TenantContextDataSource} stamps the scope onto the connection as it is
 * borrowed — before the method body runs. Elevating inside the method sets a
 * ThreadLocal for a connection already stamped DENY. It is the same trap
 * {@code TenantContextFilter} documents for the sign-in itself, which is why that
 * filter elevates the whole portal auth prefix instead.
 *
 * <p>So the fix moved the endpoint under that prefix, and this is the test that
 * would have caught the original: it asserts the read returns rows with no
 * identity, which is the only condition the login page ever runs it under.
 */
@Tag("integration")
@SpringBootTest
class StudentPortalLoginCountriesTest {

    @Autowired
    private LookupService lookupService;

    @Autowired
    private StudentUserAuthController controller;

    @Test
    @DisplayName("with no scope the underlying read is empty — the bug, pinned in place")
    void theReadIsEmptyWithoutAScope() {
        // Exactly what an anonymous request to the shared lookup endpoint got:
        // the connection is stamped DENY before the service can elevate.
        List<StudyCountryIn> countries = TenantContext.runAs(TenantContext.Scope.DENY,
                () -> lookupService.getStudyCountriesFromStudentAccount());

        assertThat(countries).isEmpty();
    }

    @Test
    @DisplayName("the portal endpoint answers with the real list, as the filter leaves it")
    void theEndpointReturnsCountriesForAnAnonymousCaller() {
        // TenantContextFilter puts every /student-portal/auth/** request in SYSTEM
        // scope before any transaction opens; this stands in for that.
        List<StudyCountryIn> countries = TenantContext.runAsSystem(controller::studyCountries);

        assertThat(countries)
                .as("the login page cannot be completed without at least one country")
                .isNotEmpty();
        assertThat(countries).allSatisfy(country ->
                assertThat(country.getId()).as("a selectable option needs an id").isNotNull());
    }
}
