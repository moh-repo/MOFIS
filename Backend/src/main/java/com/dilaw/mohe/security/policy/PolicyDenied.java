package com.dilaw.mohe.security.policy;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A policy said no.
 *
 * <p>A {@code ResponseStatusException} rather than Spring Security's
 * {@code AccessDeniedException} so that every refusal the application raises
 * — from an annotation, from a policy, from the few remaining hand-written
 * checks — reaches the client through the same handler with the same body, and
 * is audited as {@code ACCESS_DENIED} by {@code GlobalExceptionHandler} (O-32).
 * The policy and action travel with it so the audit row can say which rule
 * refused, not only that one did.
 */
public class PolicyDenied extends ResponseStatusException {

    private final String policy;
    private final String action;

    public PolicyDenied(String policy, String action, String reason) {
        super(HttpStatus.FORBIDDEN, reason);
        this.policy = policy;
        this.action = action;
    }

    public String getPolicy() {
        return policy;
    }

    public String getAction() {
        return action;
    }

    /** {@code policy=Wafar action=APPROVE}: the audit row's "what was asked". */
    public String describe() {
        return "policy=" + policy + " action=" + action;
    }
}
