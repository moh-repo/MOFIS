package com.dilaw.mohe;

import com.dilaw.mohe.util.StudentStatusTransitionValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.dilaw.mohe.util.StudentStatusTransitionValidator.*;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for student status transition rules.
 * No Spring context required — pure logic tests.
 */
class StudentValidationUnitTest {

    // ── Valid transitions ──────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → {1} should be allowed")
    @CsvSource({
            "1, 2",  // PENDING_APPROVAL → ACTIVE
            "2, 3",  // ACTIVE → SUSPENDED
            "2, 4",  // ACTIVE → GRADUATED
            "2, 5",  // ACTIVE → DEFERRED
            "2, 6",  // ACTIVE → CANCELLED
            "3, 2",  // SUSPENDED → ACTIVE
            "3, 6",  // SUSPENDED → CANCELLED
            "5, 2",  // DEFERRED → ACTIVE
            "5, 6",  // DEFERRED → CANCELLED
    })
    @DisplayName("Valid transition is accepted")
    void validTransition_noException(int from, int to) {
        assertThatNoException().isThrownBy(() -> StudentStatusTransitionValidator.validate(from, to));
    }

    // ── Invalid transitions ────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → {1} should be rejected")
    @CsvSource({
            "4, 2",  // GRADUATED → ACTIVE — not allowed
            "4, 3",  // GRADUATED → SUSPENDED
            "6, 2",  // CANCELLED → ACTIVE
            "2, 1",  // ACTIVE → PENDING_APPROVAL — backwards
    })
    @DisplayName("Invalid transition is rejected")
    void invalidTransition_throwsException(int from, int to) {
        assertThatThrownBy(() -> StudentStatusTransitionValidator.validate(from, to))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Same status (no change) is always valid")
    void sameStatus_isAlwaysValid() {
        assertThatNoException().isThrownBy(() ->
                StudentStatusTransitionValidator.validate(STATUS_ACTIVE, STATUS_ACTIVE));
        assertThatNoException().isThrownBy(() ->
                StudentStatusTransitionValidator.validate(STATUS_GRADUATED, STATUS_GRADUATED));
    }

    @Test
    @DisplayName("Null new status throws IllegalArgumentException")
    void nullNewStatus_throwsIllegalArgument() {
        assertThatThrownBy(() -> StudentStatusTransitionValidator.validate(STATUS_ACTIVE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Death transition uses resolved Deceased id, not hardcoded constant")
    void deceasedTransition_usesResolvedId() {
        int resolvedDeceasedId = 99;
        assertThatNoException().isThrownBy(() ->
                StudentStatusTransitionValidator.validate(STATUS_ACTIVE, resolvedDeceasedId, resolvedDeceasedId));
        assertThatNoException().isThrownBy(() ->
                StudentStatusTransitionValidator.validate(STATUS_DEFERRED, resolvedDeceasedId, resolvedDeceasedId));
        assertThatNoException().isThrownBy(() ->
                StudentStatusTransitionValidator.validate(STATUS_PENDING_APPROVAL, resolvedDeceasedId, resolvedDeceasedId));
    }

    @Test
    @DisplayName("Graduated students cannot be marked deceased")
    void deceasedTransition_fromGraduated_rejected() {
        int resolvedDeceasedId = 99;
        assertThatThrownBy(() ->
                StudentStatusTransitionValidator.validate(STATUS_GRADUATED, resolvedDeceasedId, resolvedDeceasedId))
                .isInstanceOf(IllegalStateException.class);
    }
}
