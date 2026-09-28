package com.dilaw.mohe.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The login throttle, and which requests it actually counts.
 *
 * <p>Until August 2026 the filter compared the whole request URI with one literal,
 * {@code /mohe/api/v1/ums/auth/login}. It was registered on the student-portal auth
 * endpoints as well and its javadoc said it covered them; it did not — every one of
 * them fell through the literal comparison, and the OTP endpoints were unthrottled.
 */
class LoginRateLimitFilterTest {

    private static final int MAX = 3;

    private static LoginRateLimitFilter filter(boolean trustForwardedFor) {
        return new LoginRateLimitFilter(MAX, new ClientIpResolver(trustForwardedFor));
    }

    private static MockHttpServletRequest post(String contextPath, String path) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", contextPath + path);
        r.setContextPath(contextPath);
        r.setRemoteAddr("10.0.0.1");
        return r;
    }

    private static MockHttpServletRequest get(String contextPath, String path) {
        MockHttpServletRequest r = post(contextPath, path);
        r.setMethod("GET");
        return r;
    }

    private static int status(LoginRateLimitFilter f, MockHttpServletRequest r) throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        f.doFilter(r, resp, chain);
        return resp.getStatus();
    }

    @Test
    @DisplayName("the staff login is throttled after the configured number of attempts")
    void staffLoginThrottled() throws Exception {
        LoginRateLimitFilter f = filter(false);
        for (int i = 0; i < MAX; i++) {
            assertThat(status(f, post("/mohe", "/api/v1/ums/auth/login"))).isEqualTo(200);
        }
        assertThat(status(f, post("/mohe", "/api/v1/ums/auth/login"))).isEqualTo(429);
    }

    @Test
    @DisplayName("the student-portal auth endpoints are throttled too — login, forgot-password, reset-password")
    void portalAuthThrottled() throws Exception {
        for (String endpoint : new String[]{"login", "forgot-password", "reset-password"}) {
            LoginRateLimitFilter f = filter(false);
            for (int i = 0; i < MAX; i++) {
                assertThat(status(f, post("/mohe", "/api/v1/student-portal/auth/" + endpoint)))
                        .as(endpoint + " attempt " + (i + 1)).isEqualTo(200);
            }
            assertThat(status(f, post("/mohe", "/api/v1/student-portal/auth/" + endpoint)))
                    .as(endpoint + " over the limit").isEqualTo(429);
        }
    }

    @Test
    @DisplayName("the match is on the context-relative path, so a different context path still throttles")
    void independentOfContextPath() throws Exception {
        LoginRateLimitFilter f = filter(false);
        for (int i = 0; i < MAX; i++) {
            status(f, post("", "/api/v1/ums/auth/login"));
        }
        assertThat(status(f, post("", "/api/v1/ums/auth/login"))).isEqualTo(429);
    }

    @Test
    @DisplayName("staff and portal buckets are separate: a burst of staff logins does not lock the portal")
    void separateBuckets() throws Exception {
        LoginRateLimitFilter f = filter(false);
        for (int i = 0; i <= MAX; i++) {
            status(f, post("/mohe", "/api/v1/ums/auth/login"));
        }
        assertThat(status(f, post("/mohe", "/api/v1/student-portal/auth/login"))).isEqualTo(200);
    }

    @Test
    @DisplayName("only POST counts on the credential endpoints — a CORS preflight or a GET does not spend the budget")
    void onlyPostCounts() throws Exception {
        LoginRateLimitFilter f = filter(false);
        for (int i = 0; i < MAX * 3; i++) {
            MockHttpServletRequest options = post("/mohe", "/api/v1/ums/auth/login");
            options.setMethod("OPTIONS");
            assertThat(status(f, options)).isEqualTo(200);

            MockHttpServletRequest get = post("/mohe", "/api/v1/ums/auth/login");
            get.setMethod("GET");
            assertThat(status(f, get)).isEqualTo(200);
        }
        assertThat(status(f, post("/mohe", "/api/v1/ums/auth/login"))).isEqualTo(200);
    }

    @Test
    @DisplayName("the security-code challenge is counted, in its own budget")
    void captchaIsThrottledSeparately() throws Exception {
        // It is a GET, and the one worth counting: every call puts a challenge
        // in the server's memory. Its own bucket, so refreshing an unreadable
        // image cannot use up the tries the sign-in itself needs.
        LoginRateLimitFilter f = filter(false);
        for (int i = 0; i < MAX; i++) {
            assertThat(status(f, get("/mohe", "/api/v1/ums/auth/captcha")))
                    .as("challenge " + (i + 1)).isEqualTo(200);
        }
        assertThat(status(f, get("/mohe", "/api/v1/ums/auth/captcha"))).isEqualTo(429);
        assertThat(status(f, post("/mohe", "/api/v1/ums/auth/login")))
                .as("the sign-in budget is untouched").isEqualTo(200);
    }

    @Test
    @DisplayName("other endpoints under the same prefixes are not throttled")
    void unrelatedPathsPass() throws Exception {
        LoginRateLimitFilter f = filter(false);
        for (int i = 0; i < MAX * 3; i++) {
            assertThat(status(f, post("/mohe", "/api/v1/ums/auth/refresh"))).isEqualTo(200);
            assertThat(status(f, post("/mohe", "/api/v1/student-portal/me/change-password"))).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("X-Forwarded-For is ignored unless the process is told it sits behind a proxy")
    void forwardedForIgnoredByDefault() throws Exception {
        LoginRateLimitFilter f = filter(false);
        for (int i = 0; i < MAX; i++) {
            MockHttpServletRequest r = post("/mohe", "/api/v1/ums/auth/login");
            r.addHeader("X-Forwarded-For", "203.0.113." + i);   // a different "client" every time
            status(f, r);
        }
        MockHttpServletRequest r = post("/mohe", "/api/v1/ums/auth/login");
        r.addHeader("X-Forwarded-For", "203.0.113.99");
        assertThat(status(f, r)).as("all from the same socket address").isEqualTo(429);
    }

    @Test
    @DisplayName("behind a trusted proxy the first forwarded address is the client")
    void forwardedForHonouredWhenTrusted() throws Exception {
        LoginRateLimitFilter f = filter(true);
        for (int i = 0; i <= MAX; i++) {
            MockHttpServletRequest r = post("/mohe", "/api/v1/ums/auth/login");
            r.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
            status(f, r);
        }
        MockHttpServletRequest other = post("/mohe", "/api/v1/ums/auth/login");
        other.addHeader("X-Forwarded-For", "203.0.113.8, 10.0.0.1");
        assertThat(status(f, other)).as("a different client behind the same proxy").isEqualTo(200);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/api/v1/ums/auth/logi%6E", "/api/v1/ums/auth/%6Cogin", "/api/v1/ums/auth/login;x=1",
            "/api/v1//ums/auth/login", "/api/v1/ums/auth/x/../login", "/api/v1/ums/./auth/login"})
    @DisplayName("SEC-06: an encoded or unnormalised spelling of the login path shares the login's budget")
    void encodedLoginPathIsThrottled(String spelling) throws Exception {
        LoginRateLimitFilter f = filter(false);
        for (int i = 0; i < MAX; i++) {
            assertThat(status(f, post("/mohe", "/api/v1/ums/auth/login"))).isEqualTo(200);
        }
        assertThat(LoginRateLimitFilter.contextRelativePath(post("/mohe", spelling)))
                .isEqualTo("/api/v1/ums/auth/login");
        assertThat(status(f, post("/mohe", spelling))).as(spelling).isEqualTo(429);
    }
}
