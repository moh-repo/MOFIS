package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.codes.UserStatus;

import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.model.UmsUserSession;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UmsAuthService {

    private static final Logger log = LoggerFactory.getLogger(UmsAuthService.class);

    @Autowired
    private UmsUserRepository userRepository;

    @Autowired
    private UmsUserService userService;

    @Autowired
    private UmsPermissionService permissionService;

    /** R-19: a logout drops the cached authorities with the session. */
    @Autowired
    private UserAuthorityCache authorityCache;

    @Autowired
    private UmsAuditLogService auditLogService;

    @Autowired
    private UmsUserSessionService sessionService;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private LoginAttemptService loginAttempts;

    /**
     * One sentence for every wrong guess, whatever was wrong about it — a
     * different message for "no such user" would be a way to enumerate accounts.
     */
    static final String INVALID_CREDENTIALS = "Invalid username or password";

    @Value("${jwt.expiration-seconds:86400}")
    private long jwtExpirationSeconds;

    private final BCryptPasswordEncoder passwordEncoder = com.dilaw.mohe.config.Passwords.encoder();

    /**
     * Authenticate user and return user info with permissions
     * 
     * @param username  Username
     * @param password  Raw password
     * @param ipAddress Client IP address
     * @param userAgent Client user agent
     * @return Authentication result with user info and permissions
     */
    @Transactional
    public AuthResult authenticate(String username, String password, String ipAddress, String userAgent) {
        if (username != null) username = username.trim();
        if (password != null) password = password.trim();
        Optional<UmsUser> userOpt = userRepository.findByUserNameAndIsDeletedFalse(username);

        if (userOpt.isEmpty()) {
            // A username with no account still climbs the ladder — in memory, and
            // with the same numbers — so this reply and the wrong-password reply
            // below cannot be told apart. See LoginAttemptService.
            log.warn("UMS login: user not found or deleted, username='{}'", username);
            auditLogService.log(null, "LOGIN_FAILED_USER_NOT_FOUND", "AUTH", null, username, null, ipAddress,
                    userAgent);
            LoginAttemptService.AttemptStatus standing = loginAttempts.currentUnknownLock(ipAddress, username);
            if (standing != null) {
                return AuthResult.failure(lockMessage(standing), standing);
            }
            // Same conditional as the wrong-password branch below, deliberately:
            // the attempt that trips the lock has to say so in both, or the
            // wording alone would tell the caller whether the account is real.
            LoginAttemptService.AttemptStatus shadow = loginAttempts.recordUnknownFailure(ipAddress, username);
            return AuthResult.failure(shadow.locked() ? lockMessage(shadow) : INVALID_CREDENTIALS, shadow);
        }

        UmsUser user = userOpt.get();

        // The lock is read before the password: an account that may not attempt
        // a sign-in should not have its password verified, and the answer must
        // not depend on whether the guess was right.
        LoginAttemptService.AttemptStatus lock = loginAttempts.currentLock(user);
        if (lock != null) {
            auditLogService.log(user.getId(), "LOGIN_FAILED_ACCOUNT_LOCKED", "AUTH", null, null, null, ipAddress,
                    userAgent);
            return AuthResult.failure(lockMessage(lock), lock);
        }

        boolean passwordOk = false;
        String storedHash = user.getPasswordHash();
        if (storedHash != null && !storedHash.isEmpty()) {
            try {
                passwordOk = passwordEncoder.matches(password, storedHash);
            } catch (Exception ignored) {
                // BCrypt failed (e.g. invalid hash format)
            }
        }
        if (!passwordOk) {
            log.warn("UMS login: invalid password for user id={}, username='{}'", user.getId(), username);
            LoginAttemptService.AttemptStatus status = loginAttempts.recordFailure(user);
            auditLogService.log(user.getId(), "LOGIN_FAILED_INVALID_PASSWORD", "AUTH", null, null, null, ipAddress,
                    userAgent);
            if (status.lockedIndefinitely()) {
                // R6-03: the last rung ends the account's sessions, as the second factor's
                // does, so an administrator's unlock opens a fresh sign-in and no old token.
                sessionService.endSessionsOf(user.getId());
                auditLogService.log(user.getId(), "SESSION_TERMINATED", "USER", user.getId(), null,
                        "account locked after too many wrong passwords", ipAddress, userAgent);
            }
            return AuthResult.failure(status.locked() ? lockMessage(status) : INVALID_CREDENTIALS, status);
        }

        // Check account status
        Optional<AccountRefusal> refusal = refusalForStatus(user.getStatus());
        if (refusal.isPresent()) {
            auditLogService.log(user.getId(), "LOGIN_FAILED_" + refusal.get().auditSuffix(), "AUTH", null, null, null,
                    ipAddress, userAgent);
            return AuthResult.failure(refusal.get().message());
        }

        // Successful login – set audit context so DB trigger sees CHANGED_BY / IP (login request has no X-User-Id)
        setAuditContextForRequest(user.getId(), ipAddress);
        // O-08: the plaintext is in hand and verified, so a hash made at the old
        // cost is re-encoded now. No forced reset; the upgrade completes login by login.
        if (com.dilaw.mohe.config.Passwords.needsRehash(storedHash)) {
            user.setPasswordHash(passwordEncoder.encode(password));
            userRepository.save(user);
        }
        loginAttempts.recordSuccess(user);
        auditLogService.log(user.getId(), "LOGIN_SUCCESS", "AUTH", null, null, null, ipAddress, userAgent);

        // Build auth result with permissions and country scope (ministry = all countries, else assigned)
        Profile profile = profileOf(user);
        return AuthResult.success(user, profile.permissions(), profile.permissionMap(), profile.countryIds(),
                profile.isMinistryUser());
    }

    /**
     * What the SPA needs to know about the signed-in account: permission codes
     * and map, country scope, and the tier (R-12). Built at login and again by
     * {@code GET /auth/me} (R-20), so a role change reaches the menus without
     * a fresh sign-in.
     */
    public record Profile(UmsUser user, List<String> permissions, Map<String, List<String>> permissionMap,
                          List<Integer> countryIds, boolean isMinistryUser, boolean isMinistryLevel,
                          String adminTier) {
    }

    public Profile profileOf(UmsUser user) {
        List<String> permissions = permissionService.getUserPermissionCodes(user.getId());
        Map<String, List<String>> permissionMap = permissionService.getPermissionMap(user.getId());
        CountryScopeService.CountryScope scope = countryScopeService.getScopeForUser(user.getId());
        return new Profile(user, permissions, permissionMap, scope.getAllowedCountryIds(), scope.isMinistryUser(),
                scope.getTier().ministryLevel(), scope.getTier().name());
    }

    public Optional<Profile> profileOf(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .map(this::profileOf);
    }

    /** Why an account in a given state may not hold a session. */
    public record AccountRefusal(String auditSuffix, String message) {
    }

    /**
     * The one status check for both sign-in and token refresh.
     *
     * <p>{@code UMSCORE.Users.Status} is {@code ACTIVE}, {@code INACTIVE} or
     * {@code LOCKED} (the last one set by {@code UmsUserService} after too many
     * wrong passwords). Only {@code ACTIVE} may hold a session; the two known
     * refusals keep their messages, and an unknown value is refused too rather
     * than admitted by omission.
     *
     * <p>Login has always checked this. {@code /refresh} did not (revision item
     * 1.8): it re-issued a token for whatever user id the old token named, so an
     * account locked or disabled by an administrator kept renewing itself for as
     * long as it refreshed before the last token expired.
     */
    public static Optional<AccountRefusal> refusalForStatus(String status) {
        if (UserStatus.ACTIVE.equals(status)) {
            return Optional.empty();
        }
        if (UserStatus.LOCKED.equals(status)) {
            return Optional.of(new AccountRefusal("ACCOUNT_LOCKED", "Account is locked. Please contact administrator."));
        }
        if (UserStatus.INACTIVE.equals(status)) {
            return Optional.of(new AccountRefusal("ACCOUNT_INACTIVE", "Account is inactive. Please contact administrator."));
        }
        return Optional.of(new AccountRefusal("ACCOUNT_NOT_ACTIVE", "Account is not active. Please contact administrator."));
    }

    /**
     * Whether {@code userId} may have its token refreshed: the row must still
     * exist, not be soft-deleted, and pass {@link #refusalForStatus}. Reloaded
     * from the database on every refresh — the token's claims say who the user
     * <em>was</em> when it was issued, not what an administrator has done since.
     *
     * @return the reason to refuse, or empty when the refresh may go ahead
     */
    @Transactional(readOnly = true)
    public Optional<String> refreshRefusal(Long userId) {
        if (userId == null) {
            return Optional.of("Token is invalid or expired. Please log in again.");
        }
        Optional<UmsUser> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty() || Boolean.TRUE.equals(userOpt.get().getIsDeleted())) {
            return Optional.of("Account no longer exists. Please log in again.");
        }
        return refusalForStatus(userOpt.get().getStatus()).map(AccountRefusal::message);
    }

    /**
     * The message for a locked account.
     *
     * <p>No countdown in the words. The deadline travels as {@code lockedUntil}
     * in {@code AuthResult.attemptStatus} and the screen renders the minutes
     * from it, in the language and the plural form the reader's language has —
     * which is the other half of the review's finding about
     * "لديك 2 محاولة(ات) متبقية".
     *
     * <p>It also has to be this way for the replies to match. A sentence built
     * from "minutes until the deadline" is computed a fraction of a second
     * after the deadline is set, so it lands on 15 or 16 depending on load; two
     * such sentences, one for an account that exists and one for a name that
     * does not, would disagree often enough to be a way of telling them apart.
     */
    private static String lockMessage(LoginAttemptService.AttemptStatus status) {
        if (status.lockedIndefinitely()) {
            return "Account is locked. Please contact administrator.";
        }
        return "Too many failed attempts. Please try again later.";
    }

    /**
     * Logout user
     * 
     * @param userId    User ID
     * @param ipAddress Client IP address
     * @param userAgent Client user agent
     */
    @Transactional
    public void logout(Long userId, String ipAddress, String userAgent) {
        // Terminate all active sessions for this user
        sessionService.terminateAllUserSessions(userId);
        authorityCache.evict(userId); // R-19
        
        // Log the logout
        auditLogService.log(userId, "LOGOUT", "AUTH", null, null, null, ipAddress, userAgent);
    }

    /**
     * Change password
     * 
     * @param userId          User ID
     * @param currentPassword Current password
     * @param newPassword     New password
     * @return true if password changed successfully
     */
    @Transactional
    public boolean changePassword(Long userId, String currentPassword, String newPassword) {
        Optional<UmsUser> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return false;
        }

        UmsUser user = userOpt.get();

        // Validate current password
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            return false;
        }

        // Update password
        userService.changePassword(userId, newPassword, userId);

        auditLogService.log(userId, "PASSWORD_CHANGED", "AUTH", null, null, null);

        return true;
    }

    /**
     * Reset password (admin function)
     * 
     * @param userId      User ID to reset
     * @param newPassword New password
     * @param adminId     Admin performing the reset
     */
    @Transactional
    public void resetPassword(Long userId, String newPassword, Long adminId) {
        userService.changePassword(userId, newPassword, adminId);
        auditLogService.log(adminId, "PASSWORD_RESET", "AUTH", userId, null, null);
    }

    // ========== Auth Result Class ==========

    public static class AuthResult {
        private boolean success;
        private String message;
        private UmsUser user;
        private List<String> permissions;
        private Map<String, List<String>> permissionMap;
        private List<Integer> countryIds;
        private boolean ministryUser;
        private LoginAttemptService.AttemptStatus attemptStatus;

        public static AuthResult failure(String message) {
            return failure(message, null);
        }

        /**
         * A refusal that also says where the caller stands on the lockout ladder,
         * so the login screen can show the truth instead of the count it used to
         * keep for itself.
         */
        public static AuthResult failure(String message, LoginAttemptService.AttemptStatus attemptStatus) {
            AuthResult result = new AuthResult();
            result.success = false;
            result.message = message;
            result.attemptStatus = attemptStatus;
            return result;
        }

        public static AuthResult success(UmsUser user, List<String> permissions,
                Map<String, List<String>> permissionMap,
                List<Integer> countryIds, boolean ministryUser) {
            AuthResult result = new AuthResult();
            result.success = true;
            result.user = user;
            result.permissions = permissions;
            result.permissionMap = permissionMap;
            result.countryIds = countryIds;
            result.ministryUser = ministryUser;
            return result;
        }

        // Getters
        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public UmsUser getUser() {
            return user;
        }

        public List<String> getPermissions() {
            return permissions;
        }

        public Map<String, List<String>> getPermissionMap() {
            return permissionMap;
        }

        public List<Integer> getCountryIds() {
            return countryIds;
        }

        public boolean isMinistryUser() {
            return ministryUser;
        }

        /** Where the caller stands on the lockout ladder, or {@code null}. */
        public LoginAttemptService.AttemptStatus getAttemptStatus() {
            return attemptStatus;
        }
    }

    /**
     * Create a new user session tied to the JWT {@code jti} claim.
     */
    @Transactional
    public void createUserSession(Long userId, String ipAddress, String userAgent, String jti) {
        try {
            UmsUserSession session = new UmsUserSession();

            Optional<UmsUser> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty() || jti == null || jti.isBlank()) {
                return;
            }
            session.setUser(userOpt.get());
            session.setTokenHash(jti);

            session.setIpAddress(ipAddress);
            session.setUserAgent(userAgent);
            // S-09: the binding the filter compares every request against.
            session.setUserAgentHash(SessionBindingService.hash(userAgent));

            String deviceInfo = extractDeviceInfo(userAgent);
            session.setDeviceInfo(deviceInfo);

            session.setExpiresAt(LocalDateTime.now().plusSeconds(jwtExpirationSeconds));
            session.setIsActive(true);

            sessionService.createSession(session);
            // O-26: the new session is kept; the oldest beyond the limit are ended and audited.
            sessionService.evictBeyondLimit(userId);
        } catch (Exception e) {
            // Log error but don't fail login
            log.error("Failed to create user session: {}", e.getMessage());
        }
    }

    /**
     * Extract device information from user agent string
     * 
     * @param userAgent User agent string
     * @return Device info string
     */
    private String extractDeviceInfo(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown Device";
        }
        
        String deviceInfo = "";
        
        // Check for mobile devices
        if (userAgent.contains("Mobile") || userAgent.contains("Android") || 
            userAgent.contains("iPhone") || userAgent.contains("iPad")) {
            deviceInfo = "Mobile";
        } else {
            deviceInfo = "Desktop";
        }
        
        // Extract browser
        if (userAgent.contains("Chrome")) {
            deviceInfo += " - Chrome";
        } else if (userAgent.contains("Firefox")) {
            deviceInfo += " - Firefox";
        } else if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) {
            deviceInfo += " - Safari";
        } else if (userAgent.contains("Edge")) {
            deviceInfo += " - Edge";
        } else if (userAgent.contains("Opera")) {
            deviceInfo += " - Opera";
        }
        
        // Extract OS
        if (userAgent.contains("Windows")) {
            deviceInfo += " (Windows)";
        } else if (userAgent.contains("Mac")) {
            deviceInfo += " (macOS)";
        } else if (userAgent.contains("Linux")) {
            deviceInfo += " (Linux)";
        } else if (userAgent.contains("Android")) {
            deviceInfo += " (Android)";
        } else if (userAgent.contains("iOS")) {
            deviceInfo += " (iOS)";
        }
        
        return deviceInfo.isEmpty() ? "Unknown Device" : deviceInfo;
    }

    /** Set request attributes so AuditContextAspect can set CONTEXT_INFO for DB triggers (e.g. login has no X-User-Id). */
    private static void setAuditContextForRequest(Long userId, String ipAddress) {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra && sra.getRequest() != null) {
                HttpServletRequest req = sra.getRequest();
                req.setAttribute("AUDIT_USER_ID", userId != null ? userId.toString() : null);
                if (ipAddress != null) {
                    req.setAttribute("AUDIT_IP", ipAddress);
                } else {
                    String x = req.getHeader("X-Forwarded-For");
                    if (x != null && !x.isEmpty()) req.setAttribute("AUDIT_IP", x.split(",")[0].trim());
                    else req.setAttribute("AUDIT_IP", req.getRemoteAddr());
                }
            }
        } catch (Exception ignored) {
        }
    }
}
