package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.ums.model.AuthLockoutPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The ladder's arithmetic, on its own.
 *
 * <p>These are the numbers the login screen shows and the lock a user waits
 * out, so they are worth pinning without a database in the way. The rungs here
 * are the ones V080 seeds: three tries then a quarter of an hour, three more
 * then an hour, three more then until an administrator unlocks.
 */
class LockoutLadderTest {

    private static final Duration WINDOW = Duration.ofMinutes(60);
    private static final Instant NOW = Instant.parse("2026-08-29T10:00:00Z");

    private static AuthLockoutPolicy stage(int order, int attempts, Integer minutes) {
        AuthLockoutPolicy p = new AuthLockoutPolicy();
        p.setStageOrder(order);
        p.setMaxAttempts(attempts);
        p.setLockMinutes(minutes);
        p.setNameAr("");
        p.setNameEn("");
        return p;
    }

    private static final List<AuthLockoutPolicy> SEEDED =
            List.of(stage(1, 3, 15), stage(2, 3, 60), stage(3, 3, null));

    @Test
    @DisplayName("the first two failures only count down; the third locks for the first rung's minutes")
    void firstRungCountsDownThenLocks() {
        LockoutLadder.Outcome one = LockoutLadder.recordFailure(SEEDED, 0, 0, null, NOW, WINDOW);
        assertThat(one.remainingAttempts()).isEqualTo(2);
        assertThat(one.locked()).isFalse();

        LockoutLadder.Outcome two = LockoutLadder.recordFailure(SEEDED, one.attempts(), one.stage(), NOW, NOW, WINDOW);
        assertThat(two.remainingAttempts()).isEqualTo(1);
        assertThat(two.locked()).isFalse();

        LockoutLadder.Outcome three = LockoutLadder.recordFailure(SEEDED, two.attempts(), two.stage(), NOW, NOW, WINDOW);
        assertThat(three.remainingAttempts()).isZero();
        assertThat(three.lockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(three.lockedIndefinitely()).isFalse();
        assertThat(three.stage()).as("standing on the second rung now").isEqualTo(1);
    }

    @Test
    @DisplayName("each rung is longer than the last, and the third holds until an administrator unlocks")
    void theLadderEscalates() {
        LockoutLadder.Outcome atSecondRung = climbOneRung(0);
        assertThat(atSecondRung.lockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));

        LockoutLadder.Outcome atThirdRung = climbOneRung(atSecondRung.stage());
        assertThat(atThirdRung.lockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
        assertThat(atThirdRung.lockedIndefinitely()).isFalse();

        LockoutLadder.Outcome last = climbOneRung(atThirdRung.stage());
        assertThat(last.lockedIndefinitely()).as("only an administrator opens this one").isTrue();
        assertThat(last.lockedUntil()).isNull();
    }

    @Test
    @DisplayName("failing again on the last rung stays on the last rung rather than falling off the end")
    void theLastRungHoldsItself() {
        LockoutLadder.Outcome beyond = climbOneRung(SEEDED.size() - 1);
        assertThat(beyond.stage()).isEqualTo(SEEDED.size() - 1);
        assertThat(beyond.lockedIndefinitely()).isTrue();
    }

    @Test
    @DisplayName("a failure older than the window starts the run over, rung included")
    void staleFailuresAreForgotten() {
        Instant longAgo = NOW.minus(Duration.ofHours(5));

        // Two failures against the second rung, but the last one was hours ago:
        // someone who mistypes once a month must not be treated as an attack.
        LockoutLadder.Outcome outcome = LockoutLadder.recordFailure(SEEDED, 2, 1, longAgo, NOW, WINDOW);

        assertThat(outcome.stage()).isZero();
        assertThat(outcome.attempts()).isEqualTo(1);
        assertThat(outcome.remainingAttempts()).isEqualTo(2);
        assertThat(outcome.locked()).isFalse();
    }

    @Test
    @DisplayName("a clean account is told the first rung's whole allowance")
    void clearStateOffersTheFirstRung() {
        assertThat(LockoutLadder.clear(SEEDED).remainingAttempts()).isEqualTo(3);
        assertThat(LockoutLadder.clear(SEEDED).locked()).isFalse();
    }

    @Test
    @DisplayName("a stage past the end of a shortened ladder falls back to the last rung")
    void aShortenedLadderDoesNotThrow() {
        // Stages can be deactivated in Settings while an account is part-way up.
        List<AuthLockoutPolicy> shortened = List.of(stage(1, 3, 15));
        assertThat(LockoutLadder.rung(shortened, 7).getLockMinutes()).isEqualTo(15);
    }

    @Test
    @DisplayName("an empty ladder names the script rather than silently letting everyone through")
    void anEmptyLadderIsADeploymentError() {
        assertThatThrownBy(() -> LockoutLadder.rung(List.of(), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("V080");
    }

    /** Fails {@code MAX_ATTEMPTS} times against the rung {@code stage} names. */
    private static LockoutLadder.Outcome climbOneRung(int stage) {
        LockoutLadder.Outcome outcome = null;
        int attempts = 0;
        for (int i = 0; i < LockoutLadder.rung(SEEDED, stage).getMaxAttempts(); i++) {
            outcome = LockoutLadder.recordFailure(SEEDED, attempts, stage, NOW, NOW, WINDOW);
            attempts = outcome.attempts();
        }
        return outcome;
    }
}
