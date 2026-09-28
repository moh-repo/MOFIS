package com.dilaw.mohe.studentportal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The origin echoed on a portal 401 is one of the configured origins, matched
 * exactly (plan item 1.13).
 *
 * <p>The check used to be {@code allowedOrigins.contains(origin)} on the raw
 * comma-separated property — a substring test. With
 * {@code https://portal.mohe.gov.ye} configured, an {@code Origin} of
 * {@code mohe.gov.ye} or {@code https://portal.mohe.gov} was reflected back
 * together with {@code Access-Control-Allow-Credentials: true}.
 */
@ExtendWith(MockitoExtension.class)
class StudentPortalTokenInterceptorTest {

    private static final String CONFIGURED = "http://localhost:4200, https://portal.mohe.gov.ye";

    @Mock private StudentUserAuthService studentUserAuthService;
    @Mock private StudentPortalAuthService legacyAuthService;
    @Mock private StudentPortalTenantScope tenantScope;
    @InjectMocks private StudentPortalTokenInterceptor interceptor;

    @Test
    @DisplayName("an exact configured origin is echoed; a prefix, suffix or substring of one is not")
    void exactMatchOnly() {
        assertThat(StudentPortalTokenInterceptor.resolveAllowedOrigin(CONFIGURED, "https://portal.mohe.gov.ye"))
                .isEqualTo("https://portal.mohe.gov.ye");
        assertThat(StudentPortalTokenInterceptor.resolveAllowedOrigin(CONFIGURED, "http://localhost:4200"))
                .isEqualTo("http://localhost:4200");

        assertThat(StudentPortalTokenInterceptor.resolveAllowedOrigin(CONFIGURED, "https://portal.mohe.gov"))
                .as("prefix").isEqualTo("http://localhost:4200");
        assertThat(StudentPortalTokenInterceptor.resolveAllowedOrigin(CONFIGURED, "mohe.gov.ye"))
                .as("suffix").isEqualTo("http://localhost:4200");
        assertThat(StudentPortalTokenInterceptor.resolveAllowedOrigin(CONFIGURED, "localhost"))
                .as("substring").isEqualTo("http://localhost:4200");
        assertThat(StudentPortalTokenInterceptor.resolveAllowedOrigin(CONFIGURED, "https://evil.example"))
                .isEqualTo("http://localhost:4200");
    }

    @Test
    @DisplayName("no Origin, or surrounding whitespace in the configuration, still resolves")
    void edges() {
        assertThat(StudentPortalTokenInterceptor.resolveAllowedOrigin(CONFIGURED, null)).isEqualTo("http://localhost:4200");
        assertThat(StudentPortalTokenInterceptor.resolveAllowedOrigin(CONFIGURED, "")).isEqualTo("http://localhost:4200");
        assertThat(StudentPortalTokenInterceptor.resolveAllowedOrigin(" https://a.example ,https://b.example ", "https://b.example"))
                .isEqualTo("https://b.example");
    }

    @Test
    @DisplayName("through preHandle: a request with no token is a 401 carrying only a configured origin")
    void preHandleWithoutToken() throws Exception {
        ReflectionTestUtils.setField(interceptor, "allowedOrigins", CONFIGURED);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mohe/api/v1/student-portal/me");
        request.addHeader("Origin", "https://portal.mohe.gov");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:4200");
    }
}
