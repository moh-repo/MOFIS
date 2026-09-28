package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.codes.UserStatus;
import com.dilaw.mohe.config.CacheConfig;
import com.dilaw.mohe.systemsettings.service.SysParameterService;
import com.dilaw.mohe.ums.model.AuthLockoutPolicy;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.repository.AuthLockoutPolicyRepository;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where failed sign-ins are counted and locks are decided — on the server.
 *
 * <p>Until the August 2026 review this lived in the browser: the login page kept
 * {@code localStorage.loginAttempts}, allowed three tries, and skipped the HTTP
 * call while it believed the account was locked. A second browser, or deleting
 * the key, removed the control. A different and invisible rule ran here at the
 * same time — five failures and the account was locked for good — so what the
 * screen promised and what the system did had never been the same thing.
 *
 * <p>Now one ladder, held in {@code MOHECORE.AUTH_LOCKOUT_POLICY}, decides both,
 * and the reply tells the screen what it is allowed to say.
 *
 * <h2>Why an unknown username is counted too</h2>
 * The reply carries how many tries remain. If it did that only for accounts
 * that exist, the counter would answer a question nobody should be able to ask
 * — whether a username is real. So a username with no row climbs the same
 * ladder in memory, keyed by address and name, and gets the same numbers back.
 * Nothing about it is persisted; it exists only to make the two replies alike.
 */
@Service
public class LoginAttemptService {

    /** How long an unclaimed shadow entry is kept before it is dropped. */
    private static final Duration SHADOW_RETENTION = Duration.ofHours(24);
    private static final int SHADOW_MAX_ENTRIES = 10_000;

    private final AuthLockoutPolicyRepository policyRepository;
    private final UmsUserRepository userRepository;
    private final SysParameterService sysParameters;
    private final com.dilaw.mohe.ums.repository.UmsUserMfaRepository mfaRepository;

    /** Ladder state for usernames with no account. Not persisted, by design. */
    private final Map<String, Shadow> shadows = new ConcurrentHashMap<>();

    public LoginAttemptService(AuthLockoutPolicyRepository policyRepository,
                               UmsUserRepository userRepository,
                               SysParameterService sysParameters,
                               com.dilaw.mohe.ums.repository.UmsUserMfaRepository mfaRepository) {
        this.policyRepository = policyRepository;
        this.userRepository = userRepository;
        this.sysParameters = sysParameters;
        this.mfaRepository = mfaRepository;
    }

    /**
     * What the caller is told after a failure, and what the login screen shows.
     *
     * @param remainingAttempts tries left before the next lock; 0 while locked
     * @param lockedUntil       when a timed lock ends, or {@code null}
     * @param lockedIndefinitely whether only an administrator can open it
     */
    public record AttemptStatus(int remainingAttempts, Instant lockedUntil, boolean lockedIndefinitely) {

        public boolean locked() {
            return lockedIndefinitely || lockedUntil != null;
        }
    }

    // ---------------------------------------------------------------- policy

    /**
     * The active rungs in order. Cached for a minute like the other catalogues,
     * so retuning a stage in Settings takes effect without a redeploy.
     */
    @Cacheable(cacheNames = CacheConfig.LOCKOUT_POLICY, key = "'stages'")
    public List<AuthLockoutPolicy> stages() {
        return List.copyOf(policyRepository.findByIsActiveTrueOrderByStageOrderAsc());
    }

    @CacheEvict(cacheNames = CacheConfig.LOCKOUT_POLICY, allEntries = true)
    public void evictPolicy() {
        // Called by the settings screen after a stage is edited.
    }

    private Duration attemptWindow() {
        return Duration.ofMinutes(sysParameters.intValue(SysParameterService.AUTH_ATTEMPT_WINDOW_MINUTES));
    }

    // ------------------------------------------------------- accounts that exist

    /**
     * The lock standing against an account, if any. An expired timed lock is
     * cleared here, so the account opens itself the moment its minutes are up
     * without waiting for someone to notice.
     *
     * @return the lock, or {@code null} when the account may attempt a sign-in
     */
    @Transactional
    public AttemptStatus currentLock(UmsUser user) {
        Instant now = Instant.now();
        if (user.getLockedUntil() != null) {
            Instant until = toInstant(user.getLockedUntil());
            if (until.isAfter(now)) {
                return new AttemptStatus(0, until, false);
            }
            user.setLockedUntil(null);
            userRepository.save(user);
        }
        if (UserStatus.LOCKED.equals(user.getStatus())) {
            return new AttemptStatus(0, null, true);
        }
        return null;
    }

    /** Applies one failed sign-in to an account that exists. */
    @Transactional
    public AttemptStatus recordFailure(UmsUser user) {
        Instant now = Instant.now();
        LockoutLadder.Outcome outcome = LockoutLadder.recordFailure(
                stages(),
                user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts(),
                user.getLockoutStage() == null ? 0 : user.getLockoutStage(),
                user.getLastFailedLoginAt() == null ? null : toInstant(user.getLastFailedLoginAt()),
                now,
                attemptWindow());

        user.setFailedLoginAttempts(outcome.attempts());
        user.setLockoutStage(outcome.stage());
        user.setLastFailedLoginAt(LocalDateTime.ofInstant(now, ZoneId.systemDefault()));
        user.setLockedUntil(outcome.lockedUntil() == null
                ? null
                : LocalDateTime.ofInstant(outcome.lockedUntil(), ZoneId.systemDefault()));
        if (outcome.lockedIndefinitely()) {
            // The last rung and the administrator's manual lock are the same
            // state on purpose: one key opens both.
            user.setStatus(UserStatus.LOCKED);
        }
        userRepository.save(user);

        return new AttemptStatus(outcome.remainingAttempts(), outcome.lockedUntil(), outcome.lockedIndefinitely());
    }

    /** Clears counter, rung and lock — a successful sign-in ends the run. */
    @Transactional
    public void recordSuccess(UmsUser user) {
        user.setFailedLoginAttempts(0);
        user.setLockoutStage(0);
        user.setLockedUntil(null);
        user.setLastFailedLoginAt(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
    }

    /**
     * Clears everything the ladder holds against an account. Used by the unlock
     * endpoint and whenever an administrator returns an account to
     * {@code ACTIVE} — which until now left the counter untouched, so the next
     * single mistake locked the account straight back.
     */
    @Transactional
    public void clearLock(UmsUser user) {
        user.setFailedLoginAttempts(0);
        user.setLockoutStage(0);
        user.setLockedUntil(null);
        user.setLastFailedLoginAt(null);
        userRepository.save(user);
        // The second factor's ladder too (N16): an account locked by wrong authenticator
        // codes would otherwise be locked again by the next single wrong code.
        mfaRepository.findById(user.getId()).ifPresent(mfa -> {
            if (mfa.getFailedAttempts() != 0 || mfa.getLockedUntil() != null) {
                mfa.setFailedAttempts(0);
                mfa.setLockedUntil(null);
                mfa.setUpdatedAt(LocalDateTime.now());
                mfaRepository.save(mfa);
            }
        });
    }

    // ---------------------------------------------------- usernames that do not

    /** The lock standing against a username with no account, if any. */
    public AttemptStatus currentUnknownLock(String ipAddress, String username) {
        Shadow shadow = shadows.get(shadowKey(ipAddress, username));
        if (shadow == null) {
            return null;
        }
        Instant now = Instant.now();
        if (shadow.lockedIndefinitely) {
            return new AttemptStatus(0, null, true);
        }
        if (shadow.lockedUntil != null && shadow.lockedUntil.isAfter(now)) {
            return new AttemptStatus(0, shadow.lockedUntil, false);
        }
        shadow.lockedUntil = null;
        return null;
    }

    /** Applies one failed sign-in to a username with no account. */
    public AttemptStatus recordUnknownFailure(String ipAddress, String username) {
        sweepShadows();
        Instant now = Instant.now();
        Shadow shadow = shadows.computeIfAbsent(shadowKey(ipAddress, username), k -> new Shadow());
        synchronized (shadow) {
            LockoutLadder.Outcome outcome = LockoutLadder.recordFailure(
                    stages(), shadow.attempts, shadow.stage, shadow.lastFailureAt, now, attemptWindow());
            shadow.attempts = outcome.attempts();
            shadow.stage = outcome.stage();
            shadow.lastFailureAt = now;
            shadow.lockedUntil = outcome.lockedUntil();
            shadow.lockedIndefinitely = outcome.lockedIndefinitely();
            shadow.touchedAt = now;
            return new AttemptStatus(outcome.remainingAttempts(), outcome.lockedUntil(), outcome.lockedIndefinitely());
        }
    }

    /** Tries left on a first attempt, so a clean form and a used one agree. */
    public int firstAttemptAllowance() {
        return LockoutLadder.rung(stages(), 0).getMaxAttempts();
    }

    private static String shadowKey(String ipAddress, String username) {
        return (ipAddress == null ? "?" : ipAddress) + "|"
                + (username == null ? "" : username.trim().toLowerCase());
    }

    /**
     * Drops entries nobody has touched for a day, and empties the map outright
     * if it ever grows past its ceiling — a guess-every-name run must not be
     * able to fill the heap.
     */
    private void sweepShadows() {
        if (shadows.size() > SHADOW_MAX_ENTRIES) {
            shadows.clear();
            return;
        }
        Instant cutoff = Instant.now().minus(SHADOW_RETENTION);
        shadows.values().removeIf(s -> s.touchedAt != null && s.touchedAt.isBefore(cutoff));
    }

    private static Instant toInstant(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    /** Ladder state for a username with no account. */
    private static final class Shadow {
        int attempts;
        int stage;
        Instant lastFailureAt;
        Instant lockedUntil;
        boolean lockedIndefinitely;
        Instant touchedAt = Instant.now();
    }
}
