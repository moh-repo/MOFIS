package com.dilaw.mohe.config.tenant;

import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.studentportal.StudentPortalTenantScope;
import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SEC-05 / SEC-08: the {@code TenantWafarWrite} key (V095) follows the account's
 * base tier, never the countries it happens to be narrowed to.
 *
 * <p>A ministry user who picks a country in the selector sends {@code X-Country-Id};
 * {@code CountryScopeService} then narrows the scope to that one country with
 * {@code ministryUser=false} so the JPA specs filter. {@code Scope.of} read that as
 * a country account and set the key to 1, switching off the database backstop
 * against ministry Wafar writes. Portal connections carried the key too.
 */
class WafarWriteFollowsBaseTierTest {

    private static final int JORDAN = 1004;

    /** The scope TenantContextFilter installs for a caller whose resolved country scope is {@code scope}. */
    private static TenantContext.Scope filtered(CountryScopeService.CountryScope scope) throws Exception {
        CountryScopeService scopes = mock(CountryScopeService.class);
        when(scopes.getScope()).thenReturn(scope);
        TenantContextFilter filter = new TenantContextFilter(scopes);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mohe/api/v1/studentdata");
        request.setContextPath("/mohe");
        AtomicReference<TenantContext.Scope> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen.set(TenantContext.current()));
        return seen.get();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AdminTier.class, names = {"SUPER_ADMIN", "MINISTRY_ADMIN", "MINISTRY_STAFF"})
    @DisplayName("a ministry-level account narrowed to one country sees that country and still writes no Wafar")
    void narrowedMinistryHasNoWriteKey(AdminTier tier) throws Exception {
        // What narrowBySelectedCountry returns: ministryUser=false, one country, the tier kept.
        CountryScopeService.CountryScope narrowed =
                new CountryScopeService.CountryScope(9L, false, true, List.of(JORDAN), tier);

        TenantContext.Scope scope = filtered(narrowed);

        assertThat(scope.all()).isFalse();
        assertThat(scope.countryIds()).containsExactly(JORDAN);
        assertThat(scope.wafarWrite()).as("TenantWafarWrite stays 0").isFalse();
    }

    @Test
    @DisplayName("positive controls: a country account keeps the key; an unnarrowed ministry account is MINISTRY")
    void countryAccountsKeepTheKey() throws Exception {
        assertThat(filtered(new CountryScopeService.CountryScope(9L, false, true, List.of(JORDAN),
                AdminTier.COUNTRY_ADMIN)).wafarWrite()).isTrue();
        assertThat(filtered(new CountryScopeService.CountryScope(9L, false, false, List.of(JORDAN),
                AdminTier.COUNTRY_STAFF)).wafarWrite()).isTrue();
        assertThat(filtered(new CountryScopeService.CountryScope(9L, true, true, List.of(JORDAN, 1005),
                AdminTier.MINISTRY_ADMIN))).isEqualTo(TenantContext.Scope.MINISTRY);
    }

    @Test
    @DisplayName("the portal scope is its student's country, read-only on Wafar")
    void portalScopeHasNoWriteKey() {
        StudentDataRepository students = mock(StudentDataRepository.class);
        when(students.findStudyCountryInByStudentId(77)).thenReturn(Optional.of(JORDAN));
        StudentPortalTenantScope portal = new StudentPortalTenantScope(students);

        TenantContext.Scope pinned = portal.of(JORDAN);
        TenantContext.Scope looked = portal.forStudent(77);

        assertThat(pinned.countryIds()).containsExactly(JORDAN);
        assertThat(pinned.wafarWrite()).isFalse();
        assertThat(looked.countryIds()).containsExactly(JORDAN);
        assertThat(looked.wafarWrite()).isFalse();
    }
}
