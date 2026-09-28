package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.config.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Student portal authentication using STUDENTCORE.student_users.
 * Username = student email. Session token returned in response and sent
 * on subsequent requests as X-Student-Portal-Token header.
 *
 * Replaces the old StudentPortalAuthController login path (kept for
 * backward compatibility with existing sessions).
 *
 * <p>Every message here follows the request's {@code Accept-Language}: the
 * login page shows the server's text in preference to its own translation,
 * so a hard-coded English refusal reached the Arabic page as English (live QA
 * M5, 24 Sep 2026). Without a message source (a unit test) the English stays.
 */
@RestController
@RequestMapping("api/v1/student-portal/auth")
public class StudentUserAuthController {

    @Autowired
    private StudentUserAuthService authService;

    @Autowired
    private StudentPortalCaptchaService captchaService;

    @Autowired
    private ClientIpResolver clientIpResolver;

    @Autowired
    private com.dilaw.mohe.service.LookupService lookupService;

    @Autowired(required = false)
    private MessageSource messageSource;

    private String msg(String key, String english) {
        return messageSource == null ? english
                : messageSource.getMessage(key, null, english, LocaleContextHolder.getLocale());
    }

    /**
     * The study countries the login page offers, read as the portal rather than as
     * nobody.
     *
     * <p>The page used to call {@code /api/v1/lookup/study-countries-from-student-account}
     * and always got an empty list, which left the country selector disabled and
     * the sign-in impossible to complete. That endpoint is {@code permitAll}, so
     * the request arrived; what stopped it was Row-Level Security. The list comes
     * from {@code MOHECORE.STUDENT_DATA}, which carries a filter predicate, and an
     * anonymous caller has no country scope, so the predicate matched nothing —
     * 941 student rows across one country read back as zero.
     *
     * <p>{@code LookupService} does wrap that read in {@code runAsSystem}, and it
     * has no effect there: the class is {@code @Transactional}, so the transaction
     * opens — and {@code TenantContextDataSource} stamps the scope onto the
     * connection as it is borrowed — before the method body runs. Elevating inside
     * the method changes a ThreadLocal for a connection already stamped DENY. This
     * is the same trap {@code TenantContextFilter} documents for the sign-in
     * itself, and the reason it elevates in the filter instead.
     *
     * <p>Which is why this lives here. Everything under the portal auth prefix is
     * already run as system by {@code TenantContextFilter}, before any transaction
     * opens, so the connection is stamped for the read that follows. The shared
     * lookup endpoint is left exactly as it was for its authenticated callers,
     * who have a scope of their own and never saw the problem.
     */
    @GetMapping("/study-countries")
    public java.util.List<com.dilaw.mohe.model.StudyCountryIn> studyCountries() {
        return lookupService.getStudyCountriesFromStudentAccount();
    }

    /** Where the challenge id travels, so the image itself stays a plain PNG. */
    static final String CAPTCHA_ID_HEADER = "X-Captcha-Id";

    /** The reply to a request that did not solve the challenge. */
    private ResponseEntity<?> captchaRefusal() {
        return ResponseEntity.status(401).body(Map.of(
                "error", msg("auth.captcha.failed", "The security code is wrong or has expired. Please try again."),
                "captchaFailed", true));
    }

    /**
     * A new security-code challenge: the id to send back with the credentials,
     * and the image to show. The answer stays on the server.
     */
    @GetMapping("/captcha")
    public ResponseEntity<byte[]> captcha(HttpServletRequest request) {
        StudentPortalCaptchaService.IssuedCaptcha issued =
                captchaService.issue(clientIpResolver.resolve(request));
        return ResponseEntity.ok()
                .header("Content-Type", MediaType.IMAGE_PNG_VALUE)
                .header("Cache-Control", "no-store")
                .header(CAPTCHA_ID_HEADER, issued.id())
                .header("Access-Control-Expose-Headers", CAPTCHA_ID_HEADER)
                .body(issued.png());
    }

    /**
     * Login with student email + password + study country.
     * Body: { "username": "...", "password": "...", "studyCountryInId": 1,
     *         "captchaId": "...", "captchaAnswer": "..." }
     * Also accepts legacy "loginKey" field for backward compatibility.
     *
     * <p>The country is required and is checked against the student's record. A
     * wrong country returns the same 401 as a wrong password — see
     * {@link StudentUserAuthService#login} for why the two must be
     * indistinguishable.
     *
     * <p>{@code captchaId} and {@code captchaAnswer} name a challenge issued by
     * {@code GET /captcha}, and they are required on <em>every</em> call — including
     * the second one that carries an e-mail or authenticator code. That is not
     * belt-and-braces: {@link StudentUserAuthService#login} verifies the password
     * before it looks at either code, so exempting code-bearing requests would
     * leave an unlimited password oracle to anyone willing to attach a junk
     * {@code totpCode}. The browser fetches a fresh challenge when the server
     * asks for a code.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> body) {
        String username = asString(body.getOrDefault("username", body.get("loginKey")));
        String password = asString(body.get("password"));
        Integer studyCountryInId = asInteger(body.get("studyCountryInId"));
        // S-11: the e-mail verification code (first sign-in) and the authenticator code.
        String emailCode = asString(body.get("emailCode"));
        String totpCode = asString(body.get("totpCode"));

        // Before the credentials are read: an unsolved challenge means the caller
        // never came through the form, and a solved one is spent either way.
        if (!captchaService.solve(asString(body.get("captchaId")), asString(body.get("captchaAnswer")))) {
            return captchaRefusal();
        }

        try {
            StudentUserAuthService.LoginStep step = authService.login(username, password, studyCountryInId, emailCode, totpCode);
            if (step == null) {
                return ResponseEntity.status(401).body(Map.of("error", msg("portal.login.invalid", "Invalid email or password")));
            }
            if (step.step() != StudentUserAuthService.Step.OK) {
                return ResponseEntity.ok(Map.of("status", step.step().name(), "message", step.message()));
            }
            StudentUserAuthService.LoginResult r = step.result();
            return ResponseEntity.ok(Map.of(
                    "status", "OK",
                    "token", r.token(),
                    "studentId", r.studentId(),
                    "studentName", r.studentName(),
                    "permissions", r.permissions(),
                    // SEC-001 (D9-a): the portal opens the password form first.
                    "mustChangePassword", r.mustChangePassword()
            ));
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getReason() != null ? ex.getReason() : msg("auth.failed", "Authentication failed")));
        }
    }

    /** JSON numbers arrive as Integer or Double; a select may also send a string. */
    private static Integer asInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            String text = value.toString().trim();
            return text.isEmpty() ? null : Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            // A malformed country is a failed login, not a 400: telling the caller
            // their country was unparseable distinguishes it from a bad password.
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestHeader(value = StudentPortalTokenInterceptor.HEADER_TOKEN, required = false) String token) {
        authService.logout(token);
        return ResponseEntity.ok(Map.of("message", msg("portal.logout.done", "Logged out")));
    }

    /**
     * Step 1: request an OTP reset code. Body: { "username": "email@..." } or legacy
     * { "loginKey": "..." }, plus the { "captchaId", "captchaAnswer" } pair.
     *
     * <p>Challenged like the sign-in. This is the one anonymous endpoint that makes
     * the server send mail on demand, so unattended it is a way to flood an
     * address and to spend the OTP budget of an account the caller does not own.
     * Step 2 ({@code /reset-password}) is left alone: it already needs a valid
     * code that only this step can mint.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", body.get("loginKey"));
        if (!captchaService.solve(body.get("captchaId"), body.get("captchaAnswer"))) {
            return captchaRefusal();
        }
        try {
            String message = authService.requestPasswordReset(username);
            return ResponseEntity.ok(Map.of("message", message));
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getReason() != null ? ex.getReason() : msg("portal.reset.requestFailed", "Failed")));
        }
    }

    /** Step 2: validate OTP and set new password. Body: { "username": "...", "otp": "...", "newPassword": "..." } */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", body.get("loginKey"));
        String otp      = body.get("otp");
        String password = body.get("newPassword");
        try {
            authService.resetPassword(username, otp, password);
            return ResponseEntity.ok(Map.of("message", msg("portal.reset.done", "Password reset successfully. You can now log in.")));
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getReason() != null ? ex.getReason() : msg("portal.reset.failed", "Failed to reset password")));
        }
    }

    // change-password moved to StudentPortalMeController (/me/change-password): it needs the
    // token interceptor, which by design does not run on the anonymous /auth/** prefix.
}
