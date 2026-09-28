package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.config.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The gate in front of the portal sign-in: a request that has not solved a
 * challenge is refused <em>before</em> the credentials are looked at.
 *
 * <p>The ordering is the whole point, so it is asserted rather than assumed. The
 * challenge exists to stop a script working through a password list, and it does
 * nothing at all if the password is checked first and the challenge only decides
 * what the reply says.
 *
 * <p>Also here: the second call of a two-step sign-in — the one carrying an
 * e-mail or authenticator code — is challenged like the first.
 * {@link StudentUserAuthService#login} verifies the password before it reads
 * either code, so an exemption there would leave an unlimited password oracle to
 * anyone willing to attach a junk {@code totpCode}.
 */
@ExtendWith(MockitoExtension.class)
class StudentPortalLoginCaptchaGateTest {

    private static final String USERNAME = "student@example.test";
    private static final String PASSWORD = "not-the-real-password";

    @Mock
    private StudentUserAuthService authService;
    @Mock
    private ClientIpResolver clientIpResolver;

    private final StudentPortalCaptchaService captchaService = new StudentPortalCaptchaService(3, 20);

    private StudentUserAuthController controller;

    @BeforeEach
    void setUp() {
        controller = new StudentUserAuthController();
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "captchaService", captchaService);
        ReflectionTestUtils.setField(controller, "clientIpResolver", clientIpResolver);
    }

    @Test
    @DisplayName("the challenge endpoint hands back a PNG and the id that goes with it")
    void issuesAnImageAndAnId() throws Exception {
        when(clientIpResolver.resolve(any())).thenReturn("10.0.0.1");

        ResponseEntity<byte[]> response = controller.captcha(new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst(StudentUserAuthController.CAPTCHA_ID_HEADER)).isNotBlank();
        assertThat(response.getHeaders().getFirst("Content-Type")).isEqualTo(MediaType.IMAGE_PNG_VALUE);
        // Caching a single-use challenge would hand the next visitor a spent one.
        assertThat(response.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
        // Without this the browser is given the image and cannot read the id.
        assertThat(response.getHeaders().getFirst("Access-Control-Expose-Headers"))
                .isEqualTo(StudentUserAuthController.CAPTCHA_ID_HEADER);
        assertThat(ImageIO.read(new ByteArrayInputStream(response.getBody()))).isNotNull();
    }

    @Test
    @DisplayName("a sign-in carrying no challenge never reaches the password check")
    void loginWithoutACaptchaIsRefusedBeforeTheCredentials() {
        ResponseEntity<?> response = controller.login(credentials(null, null));

        assertThatItWasRefusedForTheCode(response);
        verify(authService, never()).login(anyString(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("a wrong answer is refused, and does not reach the password check either")
    void loginWithAWrongAnswerIsRefused() {
        String id = issueChallenge();

        ResponseEntity<?> response = controller.login(credentials(id, "definitely-wrong"));

        assertThatItWasRefusedForTheCode(response);
        verify(authService, never()).login(anyString(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("a solved challenge is spent: the same id cannot carry a second attempt")
    void aChallengeCannotBeReplayedAcrossAttempts() {
        String id = issueChallenge();
        String answer = answerFor(id);
        when(authService.login(anyString(), anyString(), any(), any(), any())).thenReturn(null);

        assertThat(controller.login(credentials(id, answer)).getStatusCode().value())
                .as("first attempt gets as far as the credentials").isEqualTo(401);

        // The second attempt is refused for the code, not the password — which is
        // what stops one solved image being spent on a list of them.
        assertThatItWasRefusedForTheCode(controller.login(credentials(id, answer)));
        verify(authService).login(anyString(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("a solved challenge is let through to the credentials")
    void aSolvedChallengeReachesTheCredentials() {
        String id = issueChallenge();
        when(authService.login(anyString(), anyString(), any(), any(), any())).thenReturn(null);

        ResponseEntity<?> response = controller.login(credentials(id, answerFor(id)));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(bodyOf(response))
                .as("refused for the password, not the code")
                .doesNotContainKey("captchaFailed");
        verify(authService).login(USERNAME, PASSWORD, 1, null, null);
    }

    @Test
    @DisplayName("the second step of a two-step sign-in is challenged like the first")
    void theCodeBearingCallIsChallengedToo() {
        Map<String, Object> body = credentials(null, null);
        body.put("totpCode", "123456");

        ResponseEntity<?> response = controller.login(body);

        // If this were exempt, attaching any junk code would buy an unthrottled
        // password oracle: the service checks the password before it reads it.
        assertThatItWasRefusedForTheCode(response);
        verify(authService, never()).login(anyString(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("an unchallenged reset request never sends mail")
    void forgotPasswordIsChallenged() {
        Map<String, String> body = new HashMap<>();
        body.put("username", USERNAME);

        ResponseEntity<?> response = controller.forgotPassword(body);

        assertThatItWasRefusedForTheCode(response);
        verify(authService, never()).requestPasswordReset(anyString());
    }

    @Test
    @DisplayName("a solved reset request goes through")
    void forgotPasswordWithASolvedChallengeGoesThrough() {
        String id = issueChallenge();
        when(authService.requestPasswordReset(USERNAME)).thenReturn("A code was sent.");

        Map<String, String> body = new HashMap<>();
        body.put("username", USERNAME);
        body.put("captchaId", id);
        body.put("captchaAnswer", answerFor(id));

        ResponseEntity<?> response = controller.forgotPassword(body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(authService).requestPasswordReset(USERNAME);
    }

    // ── helpers ────────────────────────────────────────────────

    private String issueChallenge() {
        return captchaService.issue("10.0.0.1").id();
    }

    /** Reads the held answer directly; see StudentPortalCaptchaServiceTest for why. */
    @SuppressWarnings("unchecked")
    private String answerFor(String id) {
        try {
            var field = StudentPortalCaptchaService.class.getDeclaredField("outstanding");
            field.setAccessible(true);
            Object challenge = ((com.github.benmanes.caffeine.cache.Cache<String, Object>)
                    field.get(captchaService)).asMap().get(id);
            var text = challenge.getClass().getDeclaredMethod("text");
            text.setAccessible(true);
            return (String) text.invoke(challenge);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> credentials(String captchaId, String captchaAnswer) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", USERNAME);
        body.put("password", PASSWORD);
        body.put("studyCountryInId", 1);
        if (captchaId != null) {
            body.put("captchaId", captchaId);
        }
        if (captchaAnswer != null) {
            body.put("captchaAnswer", captchaAnswer);
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> bodyOf(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }

    private static void assertThatItWasRefusedForTheCode(ResponseEntity<?> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(bodyOf(response)).containsEntry("captchaFailed", true);
    }
}
