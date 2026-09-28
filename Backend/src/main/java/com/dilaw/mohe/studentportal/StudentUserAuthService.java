package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.codes.PortalAccountStatus;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.studentportal.model.StudentPermission;
import com.dilaw.mohe.studentportal.model.StudentSession;
import com.dilaw.mohe.studentportal.model.StudentUser;
import com.dilaw.mohe.studentportal.repository.StudentPermissionRepository;
import com.dilaw.mohe.studentportal.repository.StudentSessionRepository;
import com.dilaw.mohe.studentportal.repository.StudentUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sign-in, sign-out and the password flows of the student portal.
 *
 * <p>Every refusal and every prompt this service utters goes back to the
 * browser as it is, and the portal login page shows the server's text in
 * preference to its own translation. So the text follows the request's
 * {@code Accept-Language} through {@link #msg}: the Arabic login page answered
 * a wrong code or a weak password in English until the live test of 24 Sep
 * 2026 (M5, L12). A service built without a message source (a unit test)
 * keeps the English wording.
 */
@Service
public class StudentUserAuthService {

    private static final int SESSION_DAYS = 7;

    private final BCryptPasswordEncoder passwordEncoder = com.dilaw.mohe.config.Passwords.encoder();

    @Autowired private StudentUserRepository studentUserRepository;
    @Autowired private StudentSessionRepository sessionRepository;
    @Autowired private StudentDataRepository studentDataRepository;
    @Autowired private StudentPermissionRepository permissionRepository;
    @Autowired private StudentPortalOtpService otpService;
    @Autowired(required = false) private MessageSource messageSource;

    /** {@code key} in the request's language, or {@code english} without a message source. */
    private String msg(String key, String english, Object... args) {
        if (messageSource == null) {
            return args.length == 0 ? english : java.text.MessageFormat.format(english, args);
        }
        return messageSource.getMessage(key, args, english, LocaleContextHolder.getLocale());
    }

    /**
     * {@code mustChangePassword}: the account has never had a password of the student's
     * own choosing (QA SEC-001, D9-a); every request but the change is refused until it has.
     */
    public record LoginResult(String token, Integer studentId, String studentName, List<String> permissions,
                              boolean mustChangePassword) {}

    /**
     * S-11: what the sign-in needs next. {@code OK} carries the result; {@code
     * VERIFY_EMAIL} means a code was just sent to the account's e-mail and must
     * come back with the same credentials; {@code TOTP_REQUIRED} means the
     * account has the second factor on and the code is missing.
     */
    public enum Step { OK, VERIFY_EMAIL, TOTP_REQUIRED }

    public record LoginStep(Step step, LoginResult result, String message) {
        static LoginStep ok(LoginResult r) { return new LoginStep(Step.OK, r, null); }
        static LoginStep challenge(Step step, String message) { return new LoginStep(step, null, message); }
    }

    private static final String VERIFY_KEY_PREFIX = "verify:";

    @org.springframework.beans.factory.annotation.Autowired
    private StudentPortalMfaService mfaService;

    /**
     * Authenticates a portal student.
     *
     * <p><b>Why the country is a parameter.</b> STUDENTCORE is a separate realm
     * from UMSCORE and holds no country at all — a portal user's country exists
     * only as {@code student_id -> STUDENT_DATA.STUDY_COUNTRY_IN}. The login form
     * asks for it and this method checks it against the record, so a credential
     * that leaks cannot be used from the wrong country's portal, and so the
     * session can be stamped with a scope at the moment it is created.
     *
     * <p><b>Every failure looks identical.</b> Unknown user, wrong password,
     * inactive account and wrong country all return an empty Optional, which the
     * controller renders as the same 401. Distinguishing the country case would
     * turn this into an oracle: anyone holding a valid username could learn where
     * that student studies by trying countries until the error changed.
     *
     * <p><b>Must be called with a system scope already in force.</b> It reads
     * STUDENT_DATA, which the V031 policy filters, and a login request has no
     * scope of its own. {@link com.dilaw.mohe.config.tenant.TenantContextFilter}
     * grants it for the portal auth paths; a caller outside a request (a test,
     * a job) must wrap the call in {@code TenantContext.runAsSystem} — and must
     * do so outside this method, because the transaction borrows its connection
     * before the body runs.
     *
     * @param studyCountryInId the country the student selected on the login form
     */
    @Transactional
    /** The pre-S-11 shape: a sign-in that needs a code comes back empty, like a wrong password. */
    public Optional<LoginResult> login(String username, String password, Integer studyCountryInId) {
        LoginStep step = login(username, password, studyCountryInId, null, null);
        return step == null || step.step() != Step.OK ? Optional.empty() : Optional.of(step.result());
    }

    /**
     * S-11 sign-in: password and country as before, then the e-mail
     * verification code on the first sign-in of an unverified account, then
     * the TOTP code when the student has turned the second factor on. Null
     * means the credentials were wrong (the caller answers 401 without saying
     * which part).
     */
    public LoginStep login(String username, String password, Integer studyCountryInId, String emailCode, String totpCode) {
        if (username == null || username.isBlank() || password == null || password.isBlank()
                || studyCountryInId == null) {
            return null;
        }

        Optional<StudentUser> userOpt = studentUserRepository.findByUsernameAndIsDeletedFalse(username.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            return null;
        }

        StudentUser user = userOpt.get();

        if (!PortalAccountStatus.ACTIVE.equals(user.getStatus())) {
            return null;
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return null;
        }

        // This read is what establishes which country the caller belongs to, so it
        // cannot itself be country-scoped. The elevation that makes it work is in
        // TenantContextFilter, not here: this method is @Transactional, and
        // TenantContextDataSource stamps the scope onto a connection when it is
        // borrowed — which happens as the transaction opens, before this line. A
        // runAsSystem here would change the ThreadLocal for a connection already
        // stamped DENY and have no effect at all. That was the first attempt, and
        // StudentPortalLoginCountryTest.loginWorks_whileThePolicyIsEnabled failed
        // on it.
        Optional<StudentDatum> studentOpt = studentDataRepository.findById(user.getStudentId());
        if (studentOpt.isEmpty()) {
            // A portal account whose student record has been deleted. Same
            // response as a bad password: the account's existence is not news
            // the caller is entitled to.
            return null;
        }
        StudentDatum student = studentOpt.get();

        // The selected country must be the student's own. See the javadoc on why
        // this failure is indistinguishable from a wrong password.
        Integer actualCountry = student.getStudyCountryIn();
        if (actualCountry == null || !actualCountry.equals(studyCountryInId)) {
            return null;
        }

        // S-11 (a): the first sign-in of an account that never proved its e-mail.
        // The username is the address; the code goes there and comes back with
        // the same credentials. A wrong code is a failed sign-in.
        if (user.getEmailVerifiedAt() == null) {
            String key = VERIFY_KEY_PREFIX + user.getUsername();
            if (emailCode == null || emailCode.isBlank()) {
                otpService.generate(key, user.getUsername(), actualCountry);
                return LoginStep.challenge(Step.VERIFY_EMAIL,
                        msg("portal.login.verifyEmailSent",
                                "A verification code was sent to {0}. Enter it to finish signing in.",
                                maskEmailHint(user.getUsername()).replaceFirst("^ to ", "")));
            }
            if (!otpService.validate(key, emailCode.trim())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        msg("portal.login.emailCodeInvalid", "The verification code is wrong or has expired"));
            }
            user.setEmailVerifiedAt(Instant.now());
        }

        // S-11 (b): the optional second factor.
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            if (totpCode == null || totpCode.isBlank()) {
                return LoginStep.challenge(Step.TOTP_REQUIRED,
                        msg("portal.login.totpRequired", "Enter the code from your authenticator app."));
            }
            if (!mfaService.verifyAtLogin(user, totpCode)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        msg("portal.login.totpInvalid", "The authenticator code did not match"));
            }
        }

        user.setLastLoginAt(Instant.now());
        studentUserRepository.save(user);

        String token = UUID.randomUUID().toString().replace("-", "");
        StudentSession session = new StudentSession();
        session.setStudentUserId(user.getStudentUserId());
        session.setStudentId(user.getStudentId());
        // O-06: the row holds the hash; the raw token goes only to the browser.
        session.setToken(com.dilaw.mohe.util.PortalTokens.hash(token));
        session.setExpiresAt(Instant.now().plus(SESSION_DAYS, ChronoUnit.DAYS));
        // Pinned here so every later request gets its scope from the row the
        // interceptor already reads, with no lookup and no elevation (V036).
        session.setStudyCountryInId(actualCountry);
        sessionRepository.save(session);

        List<String> permCodes = permissionRepository
                .findEffectivePermissionsForUser(user.getStudentUserId())
                .stream()
                .map(StudentPermission::getPermissionCode)
                .toList();

        return LoginStep.ok(new LoginResult(
                token,
                user.getStudentId(),
                student.getStudentName() != null ? student.getStudentName() : "Student " + user.getStudentId(),
                permCodes,
                user.getPasswordChangedAt() == null
        ));
    }

    /**
     * @param studyCountryInId the scope pinned on the session at login. Null only
     *                         for a session issued before V036 whose backfill
     *                         found no country, which the interceptor denies.
     */
    /** {@code mustChangePassword}: see {@link LoginResult}; the token interceptor holds such a session to the change. */
    public record ResolvedStudent(Integer studentId, Integer studentUserId, Integer studyCountryInId,
                                  boolean mustChangePassword) {}

    public Optional<ResolvedStudent> resolveFromToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return sessionRepository.findByTokenAndExpiresAtAfterAndLoggedOutAtIsNull(
                        com.dilaw.mohe.util.PortalTokens.hash(token), Instant.now())
                .flatMap(s -> activeAccount(s.getStudentUserId())
                        .map(u -> new ResolvedStudent(s.getStudentId(), s.getStudentUserId(), s.getStudyCountryInId(),
                                u.getPasswordChangedAt() == null)));
    }

    /**
     * The same rule sign-in applies, on every request (live QA H4, 25 Sep 2026): a
     * session of an account that is no longer ACTIVE, or was deleted, stops working
     * at once instead of when it expires. Covers every path that changes the status,
     * the dormant-account job included.
     */
    private Optional<StudentUser> activeAccount(Integer studentUserId) {
        if (studentUserId == null) return Optional.empty();
        return studentUserRepository.findById(studentUserId)
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .filter(u -> PortalAccountStatus.ACTIVE.equals(u.getStatus()));
    }

    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) return;
        sessionRepository.findByToken(com.dilaw.mohe.util.PortalTokens.hash(token)).ifPresent(s -> {
            s.setLoggedOutAt(Instant.now());
            sessionRepository.save(s);
        });
    }

    /**
     * Step 1 of password reset: generate an OTP for the given username (email) and log/send it.
     * Returns a masked hint safe to show in the UI.
     */
    /**
     * Step 1 of password reset.
     *
     * <p>The answer is the same whether or not the e-mail belongs to an account.
     * {@link #login} goes to some length to keep a wrong country indistinguishable
     * from a wrong password; a 404 "no account for that e-mail" here would have
     * been the enumeration oracle that care was meant to prevent. So an unknown
     * e-mail mints nothing and says the same thing a known one does.
     */
    @Transactional(readOnly = true)
    public String requestPasswordReset(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg("portal.reset.emailRequired", "Email is required"));
        }
        String key = username.trim().toLowerCase();
        studentUserRepository.findByUsernameAndIsDeletedFalse(key).ifPresent(user -> {
            // username IS the email for STUDENTCORE users; the study country picks the SMTP settings.
            Integer countryId = user.getStudentId() == null ? null
                    : studentDataRepository.findById(user.getStudentId()).map(StudentDatum::getStudyCountryIn).orElse(null);
            otpService.generate(key, key, countryId);
        });
        return msg("portal.reset.sent",
                "If an account exists for {0}, a reset code has been sent to it. Check your email or contact your ministry officer.",
                maskEmailHint(key).replaceFirst("^ to ", ""));
    }

    /**
     * Step 2 of password reset: validate OTP and apply new password.
     * Also unlocks LOCKED accounts on successful reset.
     */
    @Transactional
    public void resetPassword(String username, String otp, String newPassword) {
        if (username == null || username.isBlank() || otp == null || otp.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    msg("portal.reset.emailAndOtpRequired", "Email and OTP are required"));
        }
        // O-25: the portal floor is ten characters plus the shared rules.
        com.dilaw.mohe.ums.service.PasswordPolicy.checkOrBadRequest(newPassword, username,
                com.dilaw.mohe.ums.service.PasswordPolicy.PORTAL_MIN, messageSource, LocaleContextHolder.getLocale());
        String key = username.trim().toLowerCase();
        if (!otpService.validate(key, otp.trim())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    msg("portal.reset.codeInvalid", "Invalid or expired reset code"));
        }
        StudentUser user = studentUserRepository.findByUsernameAndIsDeletedFalse(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        msg("portal.reset.accountNotFound", "Account not found")));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        studentUserRepository.save(user);
        // A reset is how a student recovers an account someone else may be using:
        // every session opened with the old password ends (FIX-PLAN H4).
        sessionRepository.deleteByStudentUserId(user.getStudentUserId());
    }

    private static String maskEmailHint(String email) {
        if (email == null || !email.contains("@")) return "";
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String masked = local.length() <= 2 ? "***" + domain
                : local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
        return " to " + masked;
    }

    /**
     * @param currentToken the token of the session making the change; every other
     *                     session of the account ends (FIX-PLAN H4), this one stays
     */
    @Transactional
    public void changePassword(Integer studentUserId, String currentPassword, String newPassword, String currentToken) {
        StudentUser user = studentUserRepository.findById(studentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    msg("portal.password.currentWrong", "Current password is incorrect"));
        }
        com.dilaw.mohe.ums.service.PasswordPolicy.checkOrBadRequest(newPassword, user.getUsername(),
                com.dilaw.mohe.ums.service.PasswordPolicy.PORTAL_MIN, messageSource, LocaleContextHolder.getLocale()); // O-25
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        studentUserRepository.save(user);
        if (currentToken != null && !currentToken.isBlank()) {
            sessionRepository.deleteOtherSessions(studentUserId, com.dilaw.mohe.util.PortalTokens.hash(currentToken));
        } else {
            sessionRepository.deleteByStudentUserId(studentUserId);
        }
    }
}
