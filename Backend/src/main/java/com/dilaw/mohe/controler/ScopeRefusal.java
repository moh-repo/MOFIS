package com.dilaw.mohe.controler;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lets a service's own refusal out of a controller's catch-all (live QA N14, 26 Sep 2026;
 * round-5 QA R5-19).
 *
 * <p>The endpoints that raise approval requests answer any exception with a 400 and its
 * message, so a student the caller may not reach came back as "400 404 NOT_FOUND ...",
 * and a Wafar-funded ticket the balance does not cover as 400 with the text
 * {@code 409 CONFLICT "Insufficient Wafar balance…"}. Every status the service chose, a
 * missing entity and an access denial are answered as themselves by
 * GlobalExceptionHandler, with the reason alone as the message (translated where the
 * refusal carries a message key); any other failure keeps the 400 the screens read.
 */
final class ScopeRefusal {

    private ScopeRefusal() {
    }

    /** Rethrows {@code e} when it is a refusal with a status of its own; returns otherwise. */
    static void rethrow(Exception e) {
        if (e instanceof ResponseStatusException r) {
            throw r;
        }
        if (e instanceof EntityNotFoundException missing) {
            throw missing;
        }
        if (e instanceof AccessDeniedException denied) {
            throw denied;
        }
    }
}
