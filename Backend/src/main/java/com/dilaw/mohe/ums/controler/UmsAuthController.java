package com.dilaw.mohe.ums.controler;

import java.util.List;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.service.MfaService;
import com.dilaw.mohe.config.ClientIpResolver;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.ums.service.JwtTokenService;
import com.dilaw.mohe.ums.service.LoginAttemptService;
import com.dilaw.mohe.ums.service.LoginCaptchaService;
import com.dilaw.mohe.ums.service.UmsAuthService;
import com.dilaw.mohe.ums.service.UmsAuthService.AuthResult;
import com.dilaw.mohe.ums.service.UmsUserSessionService;
import com.fasterxml.jackson.annotation.JsonAlias;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/ums/auth")
public class UmsAuthController {

    private static final Logger log = LoggerFactory.getLogger(UmsAuthController.class);

    @Autowired(required = false)
    private org.springframework.context.MessageSource messageSource;

    /*
     * Request bodies. These used to be @Valid Map<String, String>, which validates
     * nothing — Bean Validation has no constraints to find on a Map — so the
     * "required" checks were hand-written null tests after the fact (revision item
     * 1.8). The records carry the constraints; the JSON keys are unchanged so the
     * frontend (ums-auth.service.ts: {username, password}, {currentPassword,
     * newPassword}) is unaffected, and the legacy "userName" spelling of the login
     * key is still accepted as an alias.
     */

    /**
     * {@code POST /login} body.
     *
     * <p>{@code captchaId} and {@code captchaAnswer} name a challenge issued by
     * {@code GET /captcha}. They are required: the CAPTCHA used to be drawn and
     * marked in the browser, so a request made anywhere else never met it.
     */
    public record LoginRequest(
            @JsonAlias("userName") @NotBlank(message = "Username is required") String username,
            @NotBlank(message = "Password is required") String password,
            @NotBlank(message = "Security code is required") String captchaId,
            @NotBlank(message = "Security code is required") String captchaAnswer) {
    }

    /**
     * {@code POST /change-password} body. The length floor here gives a clean
     * validation message; the full strength rule (letter + digit) lives at the
     * choke point, {@code UmsUserService.validatePasswordStrength}.
     */
    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required") String currentPassword,
            @NotBlank(message = "New password is required")
            @Size(min = 8, message = "Password must be at least 8 characters long") String newPassword) {
    }

    @Autowired
    private UmsAuthService authService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UmsUserSessionService sessionService;

    @Autowired
    private ClientIpResolver clientIpResolver;

    @Autowired
    private LoginCaptchaService captchaService;

    @Autowired
    private MfaService mfaService;

    /** S-08: where and when a tier or role may sign in; off unless MOHE_ACCESS_RULES is set. */
    @Autowired
    private com.dilaw.mohe.ums.service.AccessRestrictions accessRestrictions;

    @Autowired
    private com.dilaw.mohe.ums.service.UmsAuditLogService auditLogService;

    @Autowired
    private com.dilaw.mohe.ums.repository.UmsUserRepository userRepository;

    @org.springframework.beans.factory.annotation.Value("${mohe.mfa.pending-token-seconds:300}")
    private long mfaPendingTokenSeconds;

    /** O-16 request bodies. {@code mfaToken} is the pending token from {@code POST /login}. */
    public record MfaVerifyRequest(@jakarta.validation.constraints.NotBlank String mfaToken,
                                   @jakarta.validation.constraints.NotBlank String code) {}
    public record MfaStartRequest(String mfaToken) {}
    public record MfaCodeRequest(String mfaToken, @jakarta.validation.constraints.NotBlank String code) {}

    /** Where the challenge id travels, so the image itself stays a plain PNG. */
    static final String CAPTCHA_ID_HEADER = "X-Captcha-Id";

    /**
     * A new security-code challenge: the id to send back with the credentials,
     * and the image to show. The answer stays on the server.
     */
    @GetMapping("/captcha")
    public ResponseEntity<byte[]> captcha() {
        LoginCaptchaService.IssuedCaptcha issued = captchaService.issue();
        return ResponseEntity.ok()
                .header("Content-Type", MediaType.IMAGE_PNG_VALUE)
                .header("Cache-Control", "no-store")
                .header(CAPTCHA_ID_HEADER, issued.id())
                .header("Access-Control-Expose-Headers", CAPTCHA_ID_HEADER)
                .body(issued.png());
    }

    /** For the {@code isMinistryLevel} flag in the login response (N-01/N-04). */
    @Autowired
    private com.dilaw.mohe.ums.service.CountryScopeService countryScopeService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest credentials, HttpServletRequest request) {
        String username = credentials.username().trim();
        String password = credentials.password().trim();

        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        // Before the credentials are read: an unsolved challenge means the caller
        // never came through the form, and a solved one is spent either way.
        if (!captchaService.solve(credentials.captchaId(), credentials.captchaAnswer())) {
            log.warn("UMS login refused for user '{}': security code missing, wrong or expired", username);
            // In the reader's language (live QA M5, 24 Sep 2026); English without a message source.
            String english = "The security code is wrong or has expired. Please try again.";
            String error = messageSource == null ? english
                    : messageSource.getMessage("auth.captcha.failed", null, english,
                            org.springframework.context.i18n.LocaleContextHolder.getLocale());
            return ResponseEntity.status(401).body(Map.of(
                    "error", error,
                    "captchaFailed", true));
        }

        AuthResult result = authService.authenticate(username, password, ipAddress, userAgent);

        if (!result.isSuccess()) {
            log.warn("UMS login failed for user '{}': {}", username, result.getMessage());
            return ResponseEntity.status(401).body(failureBody(result));
        }

        // S-08: an administrator's tier may be pinned to the ministry's address
        // blocks and office hours. Outside the blocks the sign-in is refused and
        // audited; outside the hours it goes on, but only with the second factor.
        Long userId = result.getUser().getId();
        com.dilaw.mohe.ums.service.AccessRestrictions.Outcome where =
                accessRestrictions.evaluate(userId, ipAddress, java.time.ZonedDateTime.now());
        if (where == com.dilaw.mohe.ums.service.AccessRestrictions.Outcome.DENY_IP) {
            auditLogService.log(userId, "LOGIN_DENIED_IP", "User", userId, null,
                    "Sign-in refused: address " + ipAddress + " is outside the allowlist for this account's tier or role",
                    ipAddress, userAgent);
            log.warn("UMS login refused for user '{}': address {} outside the S-08 allowlist", username, ipAddress);
            return ResponseEntity.status(403).body(Map.of(
                    "error", "Sign-in from this network is not permitted for your account.",
                    "code", "IP_NOT_ALLOWED"));
        }

        // O-16: the password is one factor. An enrolled account must present a
        // code, and a privileged account that has not enrolled must enrol, before
        // a session exists. Either way the reply carries a short-lived pending
        // token that authenticates nothing but the MFA endpoints.
        MfaService.Gate gate = mfaService.gateFor(userId);
        if (where == com.dilaw.mohe.ums.service.AccessRestrictions.Outcome.REQUIRE_MFA
                && gate == MfaService.Gate.NONE) {
            auditLogService.log(userId, "LOGIN_OUTSIDE_HOURS", "User", userId, null,
                    "Sign-in outside the configured hours: second factor required", ipAddress, userAgent);
            gate = MfaService.Gate.ENROL;
        }
        if (gate != MfaService.Gate.NONE) {
            String pending = jwtTokenService.createMfaPendingToken(result.getUser().getId(),
                    result.getUser().getUserName(), mfaPendingTokenSeconds);
            Map<String, Object> challenge = new HashMap<>();
            challenge.put("mfaRequired", gate == MfaService.Gate.VERIFY);
            challenge.put("mfaEnrolmentRequired", gate == MfaService.Gate.ENROL);
            challenge.put("mfaToken", pending);
            challenge.put("username", result.getUser().getUserName());
            return ResponseEntity.ok(challenge);
        }

        return ResponseEntity.ok(completedLogin(result.getUser(), result.getPermissions(), result.getPermissionMap(),
                result.getCountryIds(), result.isMinistryUser(), ipAddress, userAgent));
    }

    /** The session and the reply of a finished sign-in: the JWT, the account and its rights. */
    private Map<String, Object> completedLogin(UmsUser user, List<String> permissions,
                                               Map<String, List<String>> permissionMap, List<Integer> countryIds,
                                               boolean isMinistryUser, String ipAddress, String userAgent) {
        String jti = UUID.randomUUID().toString();
        String token = jwtTokenService.createToken(user.getId(), user.getUserName(), jti);
        authService.createUserSession(user.getId(), ipAddress, userAgent, jti);
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("user", user);
        response.put("permissions", permissions);
        response.put("permissionMap", permissionMap);
        response.put("countryIds", countryIds);
        response.put("isMinistryUser", isMinistryUser);
        // N-01/N-04: tells the frontend to hide Wafar writes and attaché-staff
        // administration; the server enforces regardless (CountryScopeService).
        response.put("isMinistryLevel", countryScopeService.isMinistryLevelUser(user.getId()));
        // R-12: the tier, so the SPA can stop inferring it from three flags.
        response.put("adminTier", countryScopeService.getScopeForUser(user.getId()).getTier().name());
        return response;
    }

    private Map<String, Object> completedLogin(UmsUser user, String ipAddress, String userAgent) {
        UmsAuthService.Profile p = authService.profileOf(user);
        return completedLogin(user, p.permissions(), p.permissionMap(), p.countryIds(), p.isMinistryUser(),
                ipAddress, userAgent);
    }

    // ── O-16: the second factor ──────────────────────────────────────────────

    /** The account a pending token names, or 401. */
    private UmsUser pendingUser(String mfaToken) {
        io.jsonwebtoken.Claims claims = jwtTokenService.parseToken(mfaToken);
        if (claims == null || !JwtTokenService.isMfaPending(claims) || JwtTokenService.getUserId(claims) == null) {
            throw new MfaService.MfaException("The sign-in has expired; enter your username and password again.");
        }
        return userRepository.findById(JwtTokenService.getUserId(claims))
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .orElseThrow(() -> new MfaService.MfaException("Unknown account"));
    }

    /** From a pending token when one is given (mid sign-in), else the signed-in account. */
    private Long principal(String mfaToken) {
        if (mfaToken != null && !mfaToken.isBlank()) return pendingUser(mfaToken).getId();
        return SecurityRequestUtils.requireUserId();
    }

    /** Finishes a sign-in whose password was accepted: the code, then the session. */
    @PostMapping("/mfa/verify")
    public ResponseEntity<?> mfaVerify(@Valid @RequestBody MfaVerifyRequest body, HttpServletRequest request) {
        UmsUser user = pendingUser(body.mfaToken());
        mfaService.verify(user.getId(), body.code());
        return ResponseEntity.ok(completedLogin(user, getClientIpAddress(request), request.getHeader("User-Agent")));
    }

    /** A fresh secret and its QR code, mid sign-in (pending token) or from the profile page (session). */
    @PostMapping("/mfa/enrol/start")
    public ResponseEntity<?> mfaEnrolStart(@RequestBody(required = false) MfaStartRequest body) {
        Long userId = principal(body == null ? null : body.mfaToken());
        MfaService.Enrolment e = mfaService.startEnrolment(userId);
        return ResponseEntity.ok(Map.of("secret", e.secret(), "otpauthUri", e.otpauthUri(),
                "qrPng", "data:image/png;base64," + e.qrPngBase64()));
    }

    /**
     * Turns the factor on once the app shows a code for the new secret. Mid
     * sign-in the reply is the finished sign-in plus the recovery codes; from
     * the profile page it is the recovery codes alone.
     */
    @PostMapping("/mfa/enrol/confirm")
    public ResponseEntity<?> mfaEnrolConfirm(@Valid @RequestBody MfaCodeRequest body, HttpServletRequest request) {
        boolean midSignIn = body.mfaToken() != null && !body.mfaToken().isBlank();
        UmsUser user = midSignIn ? pendingUser(body.mfaToken())
                : userRepository.findById(SecurityRequestUtils.requireUserId()).orElseThrow();
        List<String> codes = mfaService.confirmEnrolment(user.getId(), body.code());
        Map<String, Object> response = midSignIn
                ? completedLogin(user, getClientIpAddress(request), request.getHeader("User-Agent"))
                : new HashMap<>();
        response.put("recoveryCodes", codes);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/mfa/status")
    public ResponseEntity<MfaService.Status> mfaStatus() {
        return ResponseEntity.ok(mfaService.status(SecurityRequestUtils.requireUserId()));
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<?> mfaDisable(@Valid @RequestBody MfaCodeRequest body) {
        mfaService.disable(SecurityRequestUtils.requireUserId(), body.code());
        return ResponseEntity.ok(Map.of("message", "Two-factor authentication switched off"));
    }

    @PostMapping("/mfa/recovery-codes")
    public ResponseEntity<?> mfaRecoveryCodes(@Valid @RequestBody MfaCodeRequest body) {
        return ResponseEntity.ok(Map.of("recoveryCodes",
                mfaService.regenerateRecoveryCodes(SecurityRequestUtils.requireUserId(), body.code())));
    }

    /**
     * The signed-in account as the login response describes it, minus the token.
     *
     * <p>Security plan R-20 (6 Sep 2026). The SPA calls this when the window
     * regains focus and after any 403, and replaces its stored permission map,
     * so a role or country change made by an administrator reaches the menus
     * and buttons within seconds instead of at the next sign-in.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> me() {
        Long userId = SecurityRequestUtils.currentUserId().orElse(null);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }
        Optional<UmsAuthService.Profile> profile = authService.profileOf(userId);
        if (profile.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Account no longer exists"));
        }
        UmsAuthService.Profile p = profile.get();
        Map<String, Object> response = new HashMap<>();
        response.put("user", p.user());
        response.put("permissions", p.permissions());
        response.put("permissionMap", p.permissionMap());
        response.put("countryIds", p.countryIds());
        response.put("isMinistryUser", p.isMinistryUser());
        response.put("isMinistryLevel", p.isMinistryLevel());
        response.put("adminTier", p.adminTier());
        return ResponseEntity.ok(response);
    }

    /**
     * The 401 body for a refused sign-in.
     *
     * <p>It carries where the caller stands on the lockout ladder — tries left,
     * and when a lock ends — because the login screen used to keep that count in
     * {@code localStorage}, where clearing one key removed the whole control.
     * The numbers are the server's now, and they are the same for a wrong
     * password and for a username that does not exist, so reading them tells an
     * attacker nothing about who holds an account here.
     */
    private static Map<String, Object> failureBody(AuthResult result) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", result.getMessage());
        LoginAttemptService.AttemptStatus status = result.getAttemptStatus();
        if (status != null) {
            body.put("remainingAttempts", status.remainingAttempts());
            body.put("locked", status.locked());
            body.put("lockedIndefinitely", status.lockedIndefinitely());
            body.put("lockedUntil", status.lockedUntil() == null ? null : status.lockedUntil().toString());
        }
        return body;
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        Long userId = SecurityRequestUtils.currentUserId().orElse(null);
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not authenticated"));
        }
        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        authService.logout(userId, ipAddress, userAgent);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * Refresh the JWT without re-authenticating.
     * Caller must present a still-valid token in the Authorization header.
     * Returns a new token with a fresh expiry window.
     *
     * <p>Two checks stand between the old token and the new one, both reloaded
     * from the database rather than read from the token: the account must still
     * be {@code ACTIVE} and not deleted ({@link UmsAuthService#refreshRefusal}),
     * and the session the token's jti names must still be active
     * ({@link UmsUserSessionService#rotateSessionToken}). Either failing is a 401
     * — the same answer as an expired token, so the client's handling is one path.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("error", "No bearer token provided"));
        }
        String existing = authHeader.substring(7).trim();
        var claims = jwtTokenService.parseToken(existing);
        if (claims == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Token is invalid or expired. Please log in again."));
        }
        Long userId = JwtTokenService.getUserId(claims);
        Optional<String> refusal = authService.refreshRefusal(userId);
        if (refusal.isPresent()) {
            log.warn("UMS refresh refused for user id={}: {}", userId, refusal.get());
            return ResponseEntity.status(401).body(Map.of("error", refusal.get()));
        }
        String oldJti = JwtTokenService.getJti(claims);
        String newJti = UUID.randomUUID().toString();
        if (!sessionService.rotateSessionToken(userId, oldJti, newJti)) {
            return ResponseEntity.status(401).body(Map.of("error", "Token is invalid or expired. Please log in again."));
        }
        String newToken = jwtTokenService.createToken(userId, JwtTokenService.getUsername(claims), newJti);
        return ResponseEntity.ok(Map.of("token", newToken));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = SecurityRequestUtils.currentUserId().orElse(null);
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not authenticated"));
        }
        boolean success = authService.changePassword(userId, request.currentPassword(), request.newPassword());
        if (!success) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect"));
        }
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    /**
     * Administrative reset of another account's password.
     *
     * <p>Guarded like {@code POST /api/v1/ums/users/{id}/change-password}, which is
     * the endpoint the UI actually uses. This one lives under the {@code /auth}
     * prefix that the security chain leaves open for login and refresh, and until
     * August 2026 it carried no authorization at all: "some user is signed in" was
     * the only check, and {@code UmsPrivilegeGuard} only shields super-admin
     * targets — so any account could reset any other non-super-admin account,
     * including the ministry administrators. The service still runs the
     * privilege guard underneath; this annotation is the missing first gate.
     *
     * <p>The body stays a map (the {@code @Valid} that used to sit on it validated
     * nothing and is gone): {@code WriteAuthorizationTest} calls this method with a
     * map and is owned by another revision item, so the explicit checks below are
     * the validation.
     */
    @PostMapping("/reset-password")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, Object> request) {
        Long adminId = SecurityRequestUtils.currentUserId().orElse(null);
        if (adminId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Admin not authenticated"));
        }
        Long userId = request.get("userId") != null ? Long.valueOf(request.get("userId").toString()) : null;
        Object rawPassword = request.get("newPassword");
        String newPassword = rawPassword instanceof String s && !s.isBlank() ? s : null;
        if (userId == null || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User ID and new password are required"));
        }
        authService.resetPassword(userId, newPassword, adminId);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    /** The address written to the sign-in audit row; see {@link ClientIpResolver} for why the header is not trusted by default. */
    private String getClientIpAddress(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }
}
