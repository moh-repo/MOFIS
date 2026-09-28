package com.dilaw.mohe.controler;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Live QA N14: the endpoints that raise approval requests answered every failure with a
 * 400, so "a student you may not reach" read as a bad form. A scope or existence refusal
 * keeps its own status; anything else still gets the 400 the screens show. Round-5 QA
 * R5-19: every status a service chose is kept, not only 403 and 404.
 */
class ScopeRefusalTest {

    @Test
    @DisplayName("403, 404, a missing entity and an access denial are answered as themselves")
    void refusalsPassThrough() {
        assertThatThrownBy(() -> ScopeRefusal.rethrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ScopeRefusal.rethrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "no")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ScopeRefusal.rethrow(new EntityNotFoundException("gone")))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> ScopeRefusal.rethrow(new AccessDeniedException("no")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("R5-19: a service's 409 or 400 keeps its status, so its text is not wrapped in \"409 CONFLICT\"")
    void serviceStatusesPassThrough() {
        assertThatThrownBy(() -> ScopeRefusal.rethrow(new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient Wafar balance")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> ScopeRefusal.rethrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("any other failure is left to the caller's 400")
    void othersAreLeft() {
        assertThatCode(() -> ScopeRefusal.rethrow(new IllegalArgumentException("bad"))).doesNotThrowAnyException();
        assertThatCode(() -> ScopeRefusal.rethrow(new RuntimeException("Failed to create approval request: x"))).doesNotThrowAnyException();
    }
}
