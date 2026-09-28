package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.studentportal.model.StudentUser;
import com.dilaw.mohe.studentportal.repository.StudentUserRepository;
import com.dilaw.mohe.ums.service.MfaSecretCipher;
import com.dilaw.mohe.ums.service.TotpService;
import org.springframework.beans.factory.annotation.Value;
import com.dilaw.mohe.exception.LocalizedStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;

/**
 * The optional second factor for a student's portal account (security plan
 * S-11): the same TOTP the staff side uses (O-16), turned on and off by the
 * student from the portal, checked at sign-in when on. Secrets are stored
 * encrypted with the MFA key; wrong codes count and lock the factor for a
 * few minutes, as on the staff side. No recovery codes: a student who loses
 * the device asks the attaché, which resets the account through the admin
 * screen (status + password), the same path as a forgotten password.
 */
@Service
public class StudentPortalMfaService {

    private final StudentUserRepository users;
    private final TotpService totp;
    private final MfaSecretCipher cipher;
    private final String issuer;
    private final int maxFailures;
    private final int lockMinutes;

    public StudentPortalMfaService(StudentUserRepository users, TotpService totp, MfaSecretCipher cipher,
                                   @Value("${mohe.mfa.issuer:MOHE}") String issuer,
                                   @Value("${mohe.mfa.max-failures:5}") int maxFailures,
                                   @Value("${mohe.mfa.lock-minutes:15}") int lockMinutes) {
        this.users = users;
        this.totp = totp;
        this.cipher = cipher;
        this.issuer = issuer + " Student Portal";
        this.maxFailures = Math.max(1, maxFailures);
        this.lockMinutes = Math.max(1, lockMinutes);
    }

    public record Status(boolean totpEnabled, Instant totpEnrolledAt, Instant emailVerifiedAt, boolean locked) {}

    public Status status(int studentUserId) {
        StudentUser u = require(studentUserId);
        return new Status(Boolean.TRUE.equals(u.getTotpEnabled()), u.getTotpEnrolledAt(), u.getEmailVerifiedAt(), isLocked(u));
    }

    /** A fresh secret, stored but not yet enabled, and the QR code for the authenticator app. */
    @Transactional
    public Map<String, String> start(int studentUserId) {
        StudentUser u = require(studentUserId);
        if (Boolean.TRUE.equals(u.getTotpEnabled())) {
            throw new LocalizedStatusException(HttpStatus.CONFLICT, "Two-factor authentication is already enabled",
                    "portal.mfa.alreadyEnabled");
        }
        String secret = totp.generateSecret();
        u.setTotpSecretEnc(cipher.encrypt(secret));
        u.setTotpLastStep(null); // a new secret has its own steps
        u.setTotpFailedAttempts(0);
        u.setTotpLockedUntil(null);
        u.setUpdatedAt(Instant.now());
        users.save(u);
        String uri = totp.otpauthUri(issuer, u.getUsername(), secret);
        String qr = Base64.getEncoder().encodeToString(totp.qrPng(uri, 220));
        return Map.of("secret", secret, "otpauthUri", uri, "qrPng", "data:image/png;base64," + qr);
    }

    /**
     * Turns the factor on once the app shows a code for the stored secret.
     *
     * <p>A refusal does not roll back (live QA N15, 26 Sep 2026): {@link #check} stores the
     * wrong-code count and the lock before the refusal is thrown, and a rollback threw
     * them away, so turning the factor on or off never locked. Nothing else is written
     * before a refusal here.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void confirm(int studentUserId, String code) {
        StudentUser u = require(studentUserId);
        if (u.getTotpSecretEnc() == null) {
            throw new LocalizedStatusException(HttpStatus.BAD_REQUEST, "Start the set-up first", "portal.mfa.startFirst");
        }
        if (Boolean.TRUE.equals(u.getTotpEnabled())) {
            throw new LocalizedStatusException(HttpStatus.CONFLICT, "Two-factor authentication is already enabled",
                    "portal.mfa.alreadyEnabled");
        }
        // 400, not 401 (live QA M16): the portal ends the session on any 401, so a mistyped
        // code signed the student out. A wrong code is a bad entry, as a wrong current
        // password is on the password form.
        if (!check(u, code)) {
            throw new LocalizedStatusException(HttpStatus.BAD_REQUEST,
                    "The code did not match. Check the time on your device and try again.", "portal.mfa.codeMismatchSetup");
        }
        u.setTotpEnabled(true);
        u.setTotpEnrolledAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        users.save(u);
    }

    /**
     * Turns it off; a valid code is required, so a stolen open session cannot remove the
     * factor, and wrong codes count and lock as at sign-in (N15: see {@link #confirm}).
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void disable(int studentUserId, String code) {
        StudentUser u = require(studentUserId);
        if (!Boolean.TRUE.equals(u.getTotpEnabled())) {
            throw new LocalizedStatusException(HttpStatus.BAD_REQUEST, "Two-factor authentication is not enabled",
                    "portal.mfa.notEnabled");
        }
        if (!check(u, code)) {
            // 400: see confirm (M16)
            throw new LocalizedStatusException(HttpStatus.BAD_REQUEST, "The code did not match", "portal.mfa.codeMismatch");
        }
        u.setTotpEnabled(false);
        u.setTotpSecretEnc(null);
        u.setTotpLastStep(null);
        u.setTotpEnrolledAt(null);
        u.setUpdatedAt(Instant.now());
        users.save(u);
    }

    /** At sign-in: true when the code matches the enabled secret. Wrong codes count and lock. */
    @Transactional
    public boolean verifyAtLogin(StudentUser u, String code) {
        if (!Boolean.TRUE.equals(u.getTotpEnabled()) || u.getTotpSecretEnc() == null) return false;
        return check(u, code);
    }

    private boolean check(StudentUser u, String code) {
        if (isLocked(u)) {
            throw new LocalizedStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many wrong codes. Try again in a few minutes.", "portal.mfa.tooMany");
        }
        String digits = code == null ? "" : code.replaceAll("[^0-9]", "");
        long step = digits.length() == TotpService.DIGITS
                ? totp.verify(cipher.decrypt(u.getTotpSecretEnc()), digits, totp.currentStep(), 1) : -1;
        // PRT-07: a code is good once. The window accepts a step either side of now, so a
        // code seen over a shoulder or in a proxy log stayed usable for up to 90 seconds.
        // A step at or before the last one accepted is refused and counts as a wrong code,
        // as the staff MFA's LastUsedStep does.
        if (step >= 0 && u.getTotpLastStep() != null && step <= u.getTotpLastStep()) {
            step = -1;
        }
        if (step < 0) {
            int failures = (u.getTotpFailedAttempts() == null ? 0 : u.getTotpFailedAttempts()) + 1;
            u.setTotpFailedAttempts(failures);
            if (failures >= maxFailures) {
                u.setTotpLockedUntil(Instant.now().plus(lockMinutes, ChronoUnit.MINUTES));
                u.setTotpFailedAttempts(0);
            }
            users.save(u);
            return false;
        }
        u.setTotpLastStep(step);
        u.setTotpFailedAttempts(0);
        u.setTotpLockedUntil(null);
        users.save(u);
        return true;
    }

    private static boolean isLocked(StudentUser u) {
        return u.getTotpLockedUntil() != null && u.getTotpLockedUntil().isAfter(Instant.now());
    }

    private StudentUser require(int studentUserId) {
        return users.findById(studentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }
}
