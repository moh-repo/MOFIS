package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.ums.model.AuthLockoutPolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The lockout ladder's arithmetic, with no state and no database of its own.
 *
 * <p>Two callers climb it: {@link LoginAttemptService} for an account that
 * exists, whose counters live in {@code UMSCORE.Users}, and the same service's
 * in-memory shadow for a username that does not. They share this class on
 * purpose — the two paths have to produce identical numbers, or the reply to
 * "wrong password" and the reply to "no such account" would differ and the
 * attempt counter the screen shows would become a way to discover who works
 * here.
 *
 * <p>The rungs come from {@code MOHECORE.AUTH_LOCKOUT_POLICY}: try
 * {@code MAX_ATTEMPTS} times, then be locked {@code LOCK_MINUTES}; fail again
 * and the next rung applies, longer; the last rung has no minutes and holds
 * until an administrator unlocks.
 */
public final class LockoutLadder {

    private LockoutLadder() {
    }

    /**
     * What one further failure does.
     *
     * @param attempts            failures now standing against the current rung
     * @param stage               rungs already earned (0 = still on the first)
     * @param lockedUntil         when a timed lock ends, or {@code null}
     * @param lockedIndefinitely  whether only an administrator can open it
     * @param remainingAttempts   tries left before the next lock; 0 while locked
     */
    public record Outcome(int attempts, int stage, Instant lockedUntil, boolean lockedIndefinitely,
                          int remainingAttempts) {

        public boolean locked() {
            return lockedIndefinitely || lockedUntil != null;
        }
    }

    /** The state of an account with nothing held against it. */
    public static Outcome clear(List<AuthLockoutPolicy> stages) {
        return new Outcome(0, 0, null, false, rung(stages, 0).getMaxAttempts());
    }

    /**
     * Applies one failed sign-in.
     *
     * <p>A failure older than {@code window} is not part of this run: the count
     * <em>and</em> the rung reset, because the window's meaning is that those
     * earlier mistakes were unrelated — carrying the escalation forward would
     * put someone who mistypes once a month onto the hour-long rung.
     *
     * @param lastFailureAt the previous failure, or {@code null} if none
     */
    public static Outcome recordFailure(List<AuthLockoutPolicy> stages, int attempts, int stage,
                                        Instant lastFailureAt, Instant now, Duration window) {
        int countedAttempts = attempts;
        int countedStage = stage;
        if (lastFailureAt != null && lastFailureAt.isBefore(now.minus(window))) {
            countedAttempts = 0;
            countedStage = 0;
        }
        countedAttempts++;

        AuthLockoutPolicy rung = rung(stages, countedStage);
        if (countedAttempts < rung.getMaxAttempts()) {
            return new Outcome(countedAttempts, countedStage, null, false,
                    rung.getMaxAttempts() - countedAttempts);
        }

        // The rung is spent: lock, and stand on the next one for next time. The
        // last rung keeps itself, so repeated failure never falls off the end.
        int nextStage = Math.min(countedStage + 1, stages.size() - 1);
        if (rung.isOpenEnded()) {
            return new Outcome(0, nextStage, null, true, 0);
        }
        return new Outcome(0, nextStage, now.plus(Duration.ofMinutes(rung.getLockMinutes())), false, 0);
    }

    /**
     * The rung a climber on {@code stage} stands on. A stage past the end stays
     * on the last one rather than throwing: rows can be deactivated in Settings
     * while an account is already part-way up the ladder.
     */
    public static AuthLockoutPolicy rung(List<AuthLockoutPolicy> stages, int stage) {
        if (stages.isEmpty()) {
            throw new IllegalStateException(
                    "MOHECORE.AUTH_LOCKOUT_POLICY has no active stage — apply V080 or activate one");
        }
        return stages.get(Math.min(Math.max(stage, 0), stages.size() - 1));
    }
}
