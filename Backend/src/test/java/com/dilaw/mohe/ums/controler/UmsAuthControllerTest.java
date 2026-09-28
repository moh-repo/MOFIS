package com.dilaw.mohe.ums.controler;

import com.dilaw.mohe.config.ClientIpResolver;
import com.dilaw.mohe.exception.GlobalExceptionHandler;
import com.dilaw.mohe.ums.service.JwtTokenService;
import com.dilaw.mohe.ums.service.LoginAttemptService;
import com.dilaw.mohe.ums.service.LoginCaptchaService;
import com.dilaw.mohe.ums.service.UmsAuthService;
import com.dilaw.mohe.ums.service.UmsUserSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The auth controller's own checks: what {@code /refresh} refuses, and what a
 * malformed body gets.
 *
 * <p>Revision item 1.8. Refresh used to re-issue a token for whatever user id the
 * old token carried without looking at the account again, so an account locked
 * or disabled by an administrator kept renewing itself. And the request bodies
 * were {@code @Valid Map}s, which Bean Validation has nothing to say about — the
 * required-field checks were hand-written and easy to miss.
 *
 * <p>Standalone MockMvc with the real {@link JwtTokenService} (a test secret) and
 * the real exception handler, so the status codes are the ones a client sees.
 */
class UmsAuthControllerTest {

    private static final long USER = 7L;

    private final UmsAuthService authService = mock(UmsAuthService.class);
    private final UmsUserSessionService sessionService = mock(UmsUserSessionService.class);
    private final LoginCaptchaService captchaService = mock(LoginCaptchaService.class);
    private final JwtTokenService jwt = new JwtTokenService("unit-test-secret-that-is-at-least-32-chars-long", 3600);
    private MockMvc mvc;

    @BeforeEach
    void wire() {
        UmsAuthController controller = new UmsAuthController();
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "jwtTokenService", jwt);
        ReflectionTestUtils.setField(controller, "sessionService", sessionService);
        ReflectionTestUtils.setField(controller, "clientIpResolver", new ClientIpResolver(false));
        ReflectionTestUtils.setField(controller, "captchaService", captchaService);
        // Solved unless a test says otherwise — these cases are about the other
        // fields; the challenge has its own tests below.
        when(captchaService.solve(anyString(), anyString())).thenReturn(true);
        // The XML converter is on the classpath (Jasper); without an Accept header the
        // standalone setup would negotiate XML, which no real client of this API asks for.
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultRequest(post("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    private String bearer() {
        return "Bearer " + jwt.createToken(USER, "bob", "jti-1");
    }

    @Test
    @DisplayName("refresh with a LOCKED account is 401 and the session is not rotated")
    void refreshLockedAccount() throws Exception {
        when(authService.refreshRefusal(USER)).thenReturn(Optional.of("Account is locked. Please contact administrator."));

        mvc.perform(post("/api/v1/ums/auth/refresh").header("Authorization", bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Account is locked. Please contact administrator."));

        verify(sessionService, never()).rotateSessionToken(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("refresh with an ACTIVE account and a live session issues a new token on the same session")
    void refreshActiveAccount() throws Exception {
        when(authService.refreshRefusal(USER)).thenReturn(Optional.empty());
        when(sessionService.rotateSessionToken(eq(USER), eq("jti-1"), anyString())).thenReturn(true);

        mvc.perform(post("/api/v1/ums/auth/refresh").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    @DisplayName("refresh with a token whose session was revoked is 401")
    void refreshRevokedSession() throws Exception {
        when(authService.refreshRefusal(USER)).thenReturn(Optional.empty());
        when(sessionService.rotateSessionToken(eq(USER), eq("jti-1"), anyString())).thenReturn(false);

        mvc.perform(post("/api/v1/ums/auth/refresh").header("Authorization", bearer()))
                .andExpect(status().isUnauthorized());
    }

    /** A body with everything the endpoint requires except what a test replaces. */
    private static String loginBody(String fields) {
        return "{" + fields + ",\"captchaId\":\"cid\",\"captchaAnswer\":\"AB12C\"}";
    }

    @Test
    @DisplayName("login body missing the username is 400 before the service is called")
    void loginMissingUsername() throws Exception {
        mvc.perform(post("/api/v1/ums/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("\"password\":\"secret\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/ums/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("\"username\":\"   \",\"password\":\"secret\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/ums/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("\"username\":\"bob\"")))
                .andExpect(status().isBadRequest());

        verify(authService, never()).authenticate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("the legacy \"userName\" key is still accepted for the login username")
    void loginLegacyUserNameKey() throws Exception {
        when(authService.authenticate(eq("bob"), eq("secret"), any(), any()))
                .thenReturn(UmsAuthService.AuthResult.failure("Invalid username or password"));

        mvc.perform(post("/api/v1/ums/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("\"userName\":\"bob\",\"password\":\"secret\"")))
                .andExpect(status().isUnauthorized());

        verify(authService).authenticate(eq("bob"), eq("secret"), any(), any());
    }

    @Test
    @DisplayName("a login with no security code is 400; a wrong one is 401 and the credentials are never read")
    void loginNeedsASolvedCaptcha() throws Exception {
        mvc.perform(post("/api/v1/ums/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest());

        when(captchaService.solve(anyString(), anyString())).thenReturn(false);
        mvc.perform(post("/api/v1/ums/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("\"username\":\"bob\",\"password\":\"secret\"")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.captchaFailed").value(true));

        // The point of moving the check here: a caller that skips the challenge
        // never reaches the password, so the endpoint cannot be used to guess one.
        verify(authService, never()).authenticate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a refused login carries where the caller stands on the lockout ladder")
    void failureCarriesTheLadderPosition() throws Exception {
        when(authService.authenticate(eq("bob"), eq("secret"), any(), any()))
                .thenReturn(UmsAuthService.AuthResult.failure("Invalid username or password",
                        new LoginAttemptService.AttemptStatus(2, null, false)));

        mvc.perform(post("/api/v1/ums/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("\"username\":\"bob\",\"password\":\"secret\"")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.remainingAttempts").value(2))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.lockedUntil").doesNotExist());
    }

    @Test
    @DisplayName("the challenge is a PNG and its id travels in a header the browser may read")
    void captchaEndpointServesAnImage() throws Exception {
        when(captchaService.issue())
                .thenReturn(new LoginCaptchaService.IssuedCaptcha("cid-1", new byte[] {1, 2, 3}));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/ums/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Captcha-Id", "cid-1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Expose-Headers", "X-Captcha-Id"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Cache-Control", "no-store"));
    }

    @Test
    @DisplayName("change-password with a blank field is 400")
    void changePasswordBlankField() throws Exception {
        mvc.perform(post("/api/v1/ums/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"\",\"newPassword\":\"n3w\"}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).changePassword(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("only ACTIVE may hold a session; LOCKED and INACTIVE keep their messages, anything else is refused too")
    void statusRefusals() {
        assertThat(UmsAuthService.refusalForStatus("ACTIVE")).isEmpty();
        assertThat(UmsAuthService.refusalForStatus("LOCKED")).get()
                .extracting(UmsAuthService.AccountRefusal::auditSuffix).isEqualTo("ACCOUNT_LOCKED");
        assertThat(UmsAuthService.refusalForStatus("INACTIVE")).get()
                .extracting(UmsAuthService.AccountRefusal::auditSuffix).isEqualTo("ACCOUNT_INACTIVE");
        assertThat(UmsAuthService.refusalForStatus("DISABLED")).isPresent();
        assertThat(UmsAuthService.refusalForStatus(null)).isPresent();
    }
}
