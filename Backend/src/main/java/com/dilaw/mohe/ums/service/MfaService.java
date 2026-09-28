package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.model.UmsUserMfa;
import com.dilaw.mohe.ums.model.UmsUserMfaRecoveryCode;
import com.dilaw.mohe.ums.repository.UmsUserMfaRecoveryCodeRepository;
import com.dilaw.mohe.ums.repository.UmsUserMfaRepository;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Second factor for staff sign-in (security plan O-16, decision D-1 (a)).
 *
 * <p>Who must enrol: the super admin, ministry and country administrators,
 * and any account holding a privileged code ({@link PrivilegedChangeService#isPrivilegedCode}:
 * {@code *.APPROVE}, {@code SYSTEM.RESTORE}, {@code UMS_ADMIN.*}). Everyone
 * else may. {@code mohe.mfa.enforce=false} makes it optional for all.
 *
 * <p>Sign-in: the password is checked first; an enrolled account then gets a
 * pending token and must present a code ({@link #verify}); a required account
 * that has not enrolled gets a pending token and must enrol before the sign-in
 * completes. A code is accepted for one step either side of now and never
 * twice; five wrong codes lock the factor for fifteen minutes. Ten one-time
 * recovery codes are issued at enrolment and can be regenerated. An
 * administrator who may administer the account can reset its factor; the
 * account enrols again at its next sign-in. Every event is audited
 * ({@code MFA_*}) and reaches the security log (O-34).
 */
@Service
public class MfaService {

    public enum Gate { NONE, VERIFY, ENROL }

    public record Status(boolean enabled, boolean required, LocalDateTime enrolledAt, long recoveryCodesLeft,
                         boolean locked) {}

    public record Enrolment(String secret, String otpauthUri, String qrPngBase64) {}

    /** A refused code, lock or bad token: 401 with the reason. */
    public static class MfaException extends ResponseStatusException {
        public MfaException(String reason) {
            super(HttpStatus.UNAUTHORIZED, reason);
        }
    }

    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RECOVERY_CODES = 10;

    private final UmsUserMfaRepository mfaRepository;
    private final UmsUserMfaRecoveryCodeRepository recoveryRepository;
    private final UmsUserRepository userRepository;
    private final UserAuthorityCache authorityCache;
    private final AdminTierService adminTierService;
    private final TotpService totp;
    private final MfaSecretCipher cipher;
    private final UmsAuditLogService auditLogService;
    private final UmsPrivilegeGuard privilegeGuard;
    private final UmsUserService userService;
    private final SecureRandom random = new SecureRandom();

    // The factor row is flushed at once wherever it changes: the audit row that
    // follows goes through AuditContextAspect, which runs SET CONTEXT_INFO on the
    // same connection, and a batched insert still pending at that point comes
    // back with no row count and Hibernate reports it as a lost update.

    private final boolean enforce;
    private final String issuer;
    private final int maxFailures;
    /** Used only when the password's lockout policy has no timed first lock. */
    private final int lockMinutes;
    private final LoginAttemptService loginAttempts;
    private final UmsUserSessionService sessionService;

    public MfaService(UmsUserMfaRepository mfaRepository, UmsUserMfaRecoveryCodeRepository recoveryRepository,
                      UmsUserRepository userRepository, UserAuthorityCache authorityCache,
                      AdminTierService adminTierService, TotpService totp, MfaSecretCipher cipher,
                      UmsAuditLogService auditLogService, UmsPrivilegeGuard privilegeGuard,
                      @org.springframework.context.annotation.Lazy UmsUserService userService,
                      LoginAttemptService loginAttempts,
                      @org.springframework.context.annotation.Lazy UmsUserSessionService sessionService,
                      @Value("${mohe.mfa.enforce:true}") boolean enforce,
                      @Value("${mohe.mfa.issuer:MOHE}") String issuer,
                      @Value("${mohe.mfa.max-failures:5}") int maxFailures,
                      @Value("${mohe.mfa.lock-minutes:15}") int lockMinutes) {
        this.mfaRepository = mfaRepository;
        this.recoveryRepository = recoveryRepository;
        this.userRepository = userRepository;
        this.authorityCache = authorityCache;
        this.adminTierService = adminTierService;
        this.totp = totp;
        this.cipher = cipher;
        this.auditLogService = auditLogService;
        this.privilegeGuard = privilegeGuard;
        this.userService = userService;
        this.loginAttempts = loginAttempts;
        this.sessionService = sessionService;
        this.enforce = enforce;
        this.issuer = issuer;
        this.maxFailures = Math.max(1, maxFailures);
        this.lockMinutes = Math.max(1, lockMinutes);
    }

    // ── who must, who has ────────────────────────────────────────────────────

    public boolean isRequired(Long userId) {
        if (!enforce || userId == null) return false;
        AdminTier tier = adminTierService.tierOf(userId);
        if (tier == AdminTier.SUPER_ADMIN || tier == AdminTier.MINISTRY_ADMIN || tier == AdminTier.COUNTRY_ADMIN) {
            return true;
        }
        UserAuthorityCache.Snapshot snapshot = authorityCache.snapshot(userId);
        if (snapshot.deleted()) return false;
        return snapshot.codes().stream().anyMatch(PrivilegedChangeService::isPrivilegedCode);
    }

    public boolean isEnabled(Long userId) {
        return mfaRepository.findById(userId).map(UmsUserMfa::isEnabled).orElse(false);
    }

    /** What sign-in needs from this account after the password: nothing, a code, or an enrolment. */
    public Gate gateFor(Long userId) {
        if (isEnabled(userId)) return Gate.VERIFY;
        return isRequired(userId) ? Gate.ENROL : Gate.NONE;
    }

    public Status status(Long userId) {
        Optional<UmsUserMfa> row = mfaRepository.findById(userId).filter(UmsUserMfa::isEnabled);
        return new Status(row.isPresent(), isRequired(userId), row.map(UmsUserMfa::getEnrolledAt).orElse(null),
                row.isPresent() ? recoveryRepository.countByUserIdAndUsedAtIsNull(userId) : 0,
                row.map(this::isLocked).orElse(false));
    }

    // ── enrolment ────────────────────────────────────────────────────────────

    /** A fresh secret, stored but not enabled until {@link #confirmEnrolment} sees a code for it. */
    @Transactional(noRollbackFor = MfaException.class)
    public Enrolment startEnrolment(Long userId) {
        UmsUser user = requireActive(userId);
        UmsUserMfa row = mfaRepository.findById(userId).orElseGet(UmsUserMfa::new);
        if (row.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Two-factor authentication is already enabled; disable it or have an administrator reset it first.");
        }
        String secret = totp.generateSecret();
        row.setUserId(userId);
        row.setSecretEncrypted(cipher.encrypt(secret));
        row.setEnabled(false);
        row.setEnrolledAt(null);
        row.setLastUsedStep(null);
        row.setFailedAttempts(0);
        row.setLockedUntil(null);
        row.setUpdatedAt(LocalDateTime.now());
        mfaRepository.saveAndFlush(row);
        String uri = totp.otpauthUri(issuer, user.getUserName(), secret);
        String png = Base64.getEncoder().encodeToString(totp.qrPng(uri, 220));
        auditLogService.log(userId, "MFA_ENROLMENT_STARTED", "USER", userId, null, null);
        return new Enrolment(secret, uri, png);
    }

    /**
     * Enables the factor once the app proves it holds the secret; returns the recovery codes, shown once.
     * A refusal keeps its MFA_FAILED audit row (N16: it was rolled back with the refusal).
     */
    @Transactional(noRollbackFor = MfaException.class)
    public List<String> confirmEnrolment(Long userId, String code) {
        requireActive(userId);
        UmsUserMfa row = mfaRepository.findById(userId)
                .orElseThrow(() -> new MfaException("Start the enrolment first"));
        if (row.isEnabled()) throw new MfaException("Two-factor authentication is already enabled");
        String secret = cipher.decrypt(row.getSecretEncrypted());
        long step = totp.verify(secret, code, totp.currentStep(), 1);
        if (step < 0) {
            auditLogService.log(userId, "MFA_FAILED", "USER", userId, null, "enrolment: code did not match");
            throw new MfaException("The code did not match. Check the time on your device and try again.");
        }
        row.setEnabled(true);
        row.setEnrolledAt(LocalDateTime.now());
        row.setLastUsedStep(step);
        row.setFailedAttempts(0);
        row.setLockedUntil(null);
        row.setUpdatedAt(LocalDateTime.now());
        mfaRepository.saveAndFlush(row);
        List<String> codes = issueRecoveryCodes(userId);
        authorityCache.evict(userId);
        auditLogService.log(userId, "MFA_ENROLLED", "USER", userId, null, null);
        return codes;
    }

    // ── verification ─────────────────────────────────────────────────────────

    /**
     * Accepts a TOTP code (once per step) or an unused recovery code; refuses
     * anything else, counts the failure, and locks after too many.
     *
     * <p>Live QA N16 (26 Sep 2026). The count and the MFA_FAILED audit row were written
     * and then rolled back with the refusal, so a staff second factor never locked, and
     * the code step is not behind the sign-in throttle: whoever had the password could
     * try codes for as long as the pending sign-in lasted, and start another. A refusal
     * no longer rolls back, and wrong codes climb a two-rung ladder (decided 26 Sep):
     * {@code mohe.mfa.max-failures} (5) wrong codes lock the factor for as long as the
     * password's first lock ({@code AUTH_LOCKOUT_POLICY} stage 1); as many again lock the
     * account ({@code LOCKED}, the password ladder's last rung), and only an
     * administrator's unlock opens it, which clears this count as well
     * ({@link LoginAttemptService#clearLock}). A right code ends the run.
     *
     * <p>Live QA R6-03 (28 Sep 2026). The account lock clears the timed lock, and only the
     * timed lock was read here, so the right code sent on the same pending sign-in after
     * the tenth wrong one still signed in, and its token worked while the account stayed
     * {@code LOCKED}. The account's status is now read first ({@link #requireActive}):
     * no code is examined, or counted, for an account that may not hold a session.
     */
    @Transactional(noRollbackFor = MfaException.class)
    public void verify(Long userId, String code) {
        requireActive(userId);
        UmsUserMfa row = mfaRepository.findById(userId).filter(UmsUserMfa::isEnabled)
                .orElseThrow(() -> new MfaException("Two-factor authentication is not enabled for this account"));
        if (isLocked(row)) {
            auditLogService.log(userId, "MFA_FAILED", "USER", userId, null, "locked until " + row.getLockedUntil());
            throw new MfaException("Too many wrong codes. Try again in a few minutes.");
        }
        String presented = code == null ? "" : code.trim();
        String digits = presented.replaceAll("[^0-9]", "");
        if (digits.length() == TotpService.DIGITS && digits.length() == presented.replace(" ", "").length()) {
            String secret = cipher.decrypt(row.getSecretEncrypted());
            long step = totp.verify(secret, digits, totp.currentStep(), 1);
            // S-10: a secret still wrapped under the previous JWT-derived key moves to the current one.
            if (step >= 0 && cipher.needsRewrap(row.getSecretEncrypted())) {
                row.setSecretEncrypted(cipher.encrypt(secret));
            }
            if (step >= 0 && (row.getLastUsedStep() == null || step > row.getLastUsedStep())) {
                row.setLastUsedStep(step);
                row.setFailedAttempts(0);
                row.setLockedUntil(null);
                row.setUpdatedAt(LocalDateTime.now());
                mfaRepository.saveAndFlush(row);
                return;
            }
        } else if (useRecoveryCode(userId, presented)) {
            row.setFailedAttempts(0);
            row.setLockedUntil(null);
            row.setUpdatedAt(LocalDateTime.now());
            mfaRepository.saveAndFlush(row);
            auditLogService.log(userId, "MFA_RECOVERY_USED", "USER", userId, null,
                    recoveryRepository.countByUserIdAndUsedAtIsNull(userId) + " recovery code(s) left");
            return;
        }
        // The count runs across both rungs (0-4, timed lock, 5-9, account lock) and is
        // not the password's counter: a right password resets that one before the code is
        // asked for, which would wipe the wrong codes.
        int failures = row.getFailedAttempts() + 1;
        row.setFailedAttempts(failures);
        String detail = "wrong code (" + failures + " of " + (2 * maxFailures) + ")";
        String message = "The code is wrong or has already been used.";
        boolean lockAccount = failures >= 2 * maxFailures;
        if (lockAccount) {
            row.setLockedUntil(null);
            detail = "account locked after " + failures + " wrong codes";
            message = "Too many wrong codes. The account is locked; ask an administrator to unlock it.";
        } else if (failures == maxFailures) {
            int minutes = firstLockMinutes();
            row.setLockedUntil(LocalDateTime.now().plusMinutes(minutes));
            detail = "locked for " + minutes + " minutes after " + failures + " wrong codes";
            message = "Too many wrong codes. Try again in " + minutes + " minutes.";
        }
        row.setUpdatedAt(LocalDateTime.now());
        mfaRepository.saveAndFlush(row);
        auditLogService.log(userId, "MFA_FAILED", "USER", userId, null, detail);
        if (lockAccount) {
            lockAccount(userId, failures);
        }
        throw new MfaException(message);
    }

    /** The password ladder's first timed lock, so one setting governs both; the MFA setting when there is none. */
    private int firstLockMinutes() {
        try {
            List<com.dilaw.mohe.ums.model.AuthLockoutPolicy> stages = loginAttempts.stages();
            Integer minutes = stages.isEmpty() ? null : stages.get(0).getLockMinutes();
            return minutes != null && minutes > 0 ? minutes : lockMinutes;
        } catch (RuntimeException noPolicy) {
            return lockMinutes;
        }
    }

    /**
     * The ladder's last rung: the account is {@code LOCKED}, as after the password
     * ladder's last rung, and its open sessions end (the code may have come from the
     * profile page, where a session exists).
     */
    private void lockAccount(Long userId, int failures) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setStatus(com.dilaw.mohe.codes.UserStatus.LOCKED);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.saveAndFlush(user);
        });
        sessionService.endSessionsOf(userId);
        authorityCache.evict(userId);
        auditLogService.log(userId, "ACCOUNT_LOCKED", "USER", userId, null,
                "locked after " + failures + " wrong authenticator codes");
    }

    // ── self-service and administration ──────────────────────────────────────

    /**
     * The account turns its own factor off; refused while the account is one that must have it.
     * Wrong codes count as at sign-in (N16: see {@link #verify}).
     */
    @Transactional(noRollbackFor = MfaException.class)
    public void disable(Long userId, String code) {
        if (isRequired(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Two-factor authentication is required for this account and cannot be switched off.");
        }
        verify(userId, code);
        removeFactor(userId);
        auditLogService.log(userId, "MFA_DISABLED", "USER", userId, null, null);
    }

    /** New recovery codes for a right code; wrong codes count as at sign-in (N16: see {@link #verify}). */
    @Transactional(noRollbackFor = MfaException.class)
    public List<String> regenerateRecoveryCodes(Long userId, String code) {
        verify(userId, code);
        List<String> codes = issueRecoveryCodes(userId);
        auditLogService.log(userId, "MFA_RECOVERY_REGENERATED", "USER", userId, null, null);
        return codes;
    }

    /** An administrator clears the factor (lost phone); the account enrols again at its next sign-in. */
    @Transactional
    public void reset(Long targetUserId, Long adminId) {
        // The account must be one the caller can see (404 otherwise, as everywhere
        // in UmsUserService: a 403 would confirm the id exists) and one the caller
        // may administer (N-01, UmsUserPolicy).
        UmsUser target = userService.require(targetUserId);
        privilegeGuard.assertMayAdminister(targetUserId);
        boolean had = isEnabled(targetUserId);
        removeFactor(targetUserId);
        auditLogService.log(adminId, "MFA_RESET", "USER", targetUserId, had ? "enabled" : "not enrolled",
                target.getUserName());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Refuses an account that may not hold a session: deleted, or not {@code ACTIVE}
     * ({@link UmsAuthService#refusalForStatus}, the check sign-in and refresh make).
     * Sign-in refuses such an account before any pending token exists, so one reaching
     * a code step was locked or disabled during its pending sign-in (R6-03).
     */
    private UmsUser requireActive(Long userId) {
        UmsUser user = userRepository.findById(userId)
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .orElseThrow(() -> new MfaException("Unknown account"));
        Optional<UmsAuthService.AccountRefusal> refusal = UmsAuthService.refusalForStatus(user.getStatus());
        if (refusal.isPresent()) {
            auditLogService.log(userId, "MFA_FAILED", "USER", userId, null, "account " + user.getStatus());
            throw new MfaException(refusal.get().message());
        }
        return user;
    }

    private boolean isLocked(UmsUserMfa row) {
        return row.getLockedUntil() != null && row.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private void removeFactor(Long userId) {
        recoveryRepository.deleteByUserId(userId);
        mfaRepository.deleteById(userId);
        authorityCache.evict(userId);
    }

    private List<String> issueRecoveryCodes(Long userId) {
        recoveryRepository.deleteByUserId(userId);
        List<String> plain = new ArrayList<>(RECOVERY_CODES);
        for (int i = 0; i < RECOVERY_CODES; i++) {
            StringBuilder sb = new StringBuilder(11);
            for (int j = 0; j < 10; j++) {
                if (j == 5) sb.append('-');
                sb.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
            }
            String code = sb.toString();
            plain.add(code);
            UmsUserMfaRecoveryCode row = new UmsUserMfaRecoveryCode();
            row.setUserId(userId);
            row.setCodeHash(cipher.hmac(normaliseRecovery(code)));
            recoveryRepository.save(row);
        }
        return plain;
    }

    private boolean useRecoveryCode(Long userId, String presented) {
        // Under the current key or an older one (W5-04): a code issued before a key rotation still works.
        java.util.List<String> hashes = cipher.hmacCandidates(normaliseRecovery(presented));
        for (UmsUserMfaRecoveryCode row : recoveryRepository.findByUserId(userId)) {
            if (row.getUsedAt() == null && hashes.contains(row.getCodeHash())) {
                row.setUsedAt(LocalDateTime.now());
                recoveryRepository.save(row);
                return true;
            }
        }
        return false;
    }

    private static String normaliseRecovery(String code) {
        return code == null ? "" : code.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }
}
