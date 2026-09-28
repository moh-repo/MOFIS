package com.dilaw.mohe.config.tenant;

import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which requests run before identity, and so in the system scope (plan W5-05).
 * The staff sign-in page's country list is one of them: with the staff policy on,
 * an anonymous read of {@code UserCountries} returned no rows and the list was empty.
 */
class TenantContextFilterPathsTest {

    private static TenantContext.Scope scopeFor(String uri) throws Exception {
        CountryScopeService scopes = mock(CountryScopeService.class);
        when(scopes.getScope()).thenReturn(CountryScopeService.CountryScope.noAccess());
        TenantContextFilter filter = new TenantContextFilter(scopes);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mohe" + uri);
        request.setContextPath("/mohe");
        AtomicReference<TenantContext.Scope> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen.set(TenantContext.current()));
        return seen.get();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/v1/lookup/study-countries-for-login", "/api/v1/lookup/study-countries-for-login/",
            "/api/v1/ums/auth/login", "/api/v1/student-portal/auth/countries"})
    @DisplayName("a sign-in step runs in the system scope")
    void signInStepsRunAsSystem(String uri) throws Exception {
        assertThat(scopeFor(uri)).isEqualTo(TenantContext.Scope.SYSTEM);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/v1/lookup/study-countries", "/api/v1/lookup/study-countries-for-login-x",
            "/api/v1/ums/auth/me"})
    @DisplayName("anything else gets the caller's own scope, never the system one")
    void otherPathsDoNot(String uri) throws Exception {
        assertThat(scopeFor(uri)).isNotEqualTo(TenantContext.Scope.SYSTEM);
    }
}
