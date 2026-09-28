package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentPortalAccount;
import com.dilaw.mohe.model.StudentPortalSession;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentPortalAccountRepository;
import com.dilaw.mohe.repository.StudentPortalSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class StudentPortalAuthService {

    private static final int SESSION_DAYS = 7;
    private final BCryptPasswordEncoder passwordEncoder = com.dilaw.mohe.config.Passwords.encoder();

    @Autowired
    private StudentPortalAccountRepository accountRepository;
    @Autowired
    private StudentPortalSessionRepository sessionRepository;
    @Autowired
    private StudentDataRepository studentDataRepository;
    @Autowired
    private StudentPortalOtpService otpService;

    @Transactional
    public Optional<StudentPortalLoginResult> login(String loginKey, String password) {
        if (loginKey == null || loginKey.isBlank() || password == null || password.isBlank()) {
            return Optional.empty();
        }
        Optional<StudentPortalAccount> accountOpt = accountRepository.findByLoginKeyAndIsActiveTrue(loginKey.trim());
        if (accountOpt.isEmpty()) {
            return Optional.empty();
        }
        StudentPortalAccount account = accountOpt.get();
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            return Optional.empty();
        }
        // Unreachable over HTTP: StudentPortalAuthController had its @RestController
        // removed when STUDENTCORE auth superseded it, so nothing maps /login here.
        // Only resolveStudentIdFromToken is still live, for tokens issued before
        // that migration. The elevation is correct regardless — a login carries no
        // scope, so under the V031 policy this read would return nothing and the
        // method would report a missing student for a valid account, exactly as
        // StudentUserAuthService did. Fixed rather than left as a trap for whoever
        // remaps this controller.
        Optional<StudentDatum> studentOpt = com.dilaw.mohe.config.tenant.TenantContext.runAsSystem(
                () -> studentDataRepository.findById(account.getStudentId()));
        if (studentOpt.isEmpty()) {
            return Optional.empty();
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plus(SESSION_DAYS, ChronoUnit.DAYS);
        StudentPortalSession session = new StudentPortalSession();
        session.setStudentId(account.getStudentId());
        session.setToken(com.dilaw.mohe.util.PortalTokens.hash(token)); // O-06
        session.setExpiresAt(expiresAt);
        sessionRepository.save(session);

        account.setLastLoginAt(Instant.now());
        accountRepository.save(account);

        StudentDatum student = studentOpt.get();
        return Optional.of(new StudentPortalLoginResult(
                token,
                account.getStudentId(),
                student.getStudentName() != null ? student.getStudentName() : ("Student " + account.getStudentId())
        ));
    }

    public Optional<Integer> resolveStudentIdFromToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return sessionRepository.findByTokenAndExpiresAtAfter(com.dilaw.mohe.util.PortalTokens.hash(token), Instant.now())
                .map(StudentPortalSession::getStudentId);
    }

    /**
     * Generates and "sends" a one-time password for the given loginKey.
     * Returns a hint message safe to show in the UI (no PII).
     */
    @Transactional(readOnly = true)
    public String requestPasswordReset(String loginKey) {
        if (loginKey == null || loginKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login key is required");
        }
        StudentPortalAccount account = accountRepository.findByLoginKeyAndIsActiveTrue(loginKey.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No active account found for the provided login key"));
        otpService.generate(loginKey.trim(), account.getEmail());
        // Return a masked hint — do not expose the actual email in the response
        String emailHint = maskEmail(account.getEmail());
        return "A reset code has been issued" + (emailHint != null ? " to " + emailHint : "") + ". Check your registered email or contact your ministry officer.";
    }

    /**
     * Validates the OTP and sets a new password.
     */
    @Transactional
    public void resetPassword(String loginKey, String otp, String newPassword) {
        if (loginKey == null || loginKey.isBlank() || otp == null || otp.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login key and OTP are required");
        }
        com.dilaw.mohe.ums.service.PasswordPolicy.checkOrBadRequest(newPassword, loginKey,
                com.dilaw.mohe.ums.service.PasswordPolicy.PORTAL_MIN); // O-25
        if (!otpService.validate(loginKey.trim(), otp.trim())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired reset code");
        }
        StudentPortalAccount account = accountRepository.findByLoginKeyAndIsActiveTrue(loginKey.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        accountRepository.save(account);
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return null;
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) return "***" + domain;
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessionRepository.findByTokenAndExpiresAtAfter(com.dilaw.mohe.util.PortalTokens.hash(token), Instant.now())
                    .ifPresent(session -> sessionRepository.delete(session));
        }
    }

    public static class StudentPortalLoginResult {
        private final String token;
        private final int studentId;
        private final String studentName;

        public StudentPortalLoginResult(String token, int studentId, String studentName) {
            this.token = token;
            this.studentId = studentId;
            this.studentName = studentName;
        }

        public String getToken() { return token; }
        public int getStudentId() { return studentId; }
        public String getStudentName() { return studentName; }
    }
}
