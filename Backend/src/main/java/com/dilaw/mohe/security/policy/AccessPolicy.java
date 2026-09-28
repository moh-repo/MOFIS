package com.dilaw.mohe.security.policy;

import java.io.Serializable;
import java.util.Optional;

/**
 * One resource type's access rules, as code.
 *
 * <p>Security plan R-16 (B.3): policies are Java classes, one per resource
 * type, unit-testable and versioned with the code; only the <em>data</em>
 * (who holds which role, which countries, which position) is administered in
 * the UI. A policy answers a single question — may this subject do this action
 * to this resource — and never audits, loads lazily, or throws: it returns a
 * {@link Decision} and {@link AccessControl} does the rest.
 *
 * <p>{@code resource} may be null for subject-only questions ("may this
 * account record Wafar at all"), which is how a controller asks before the row
 * exists: {@code hasPermission('Wafar', 'RECORD')}.
 *
 * @param <R> the resource the policy judges
 */
public interface AccessPolicy<R> {

    /** The name used in {@code hasPermission(id, 'Name', 'ACTION')} and in audit rows. */
    String name();

    Class<R> resourceType();

    /** Whether this policy judges values of {@code type}; the default is by {@link #resourceType()}. */
    default boolean handles(Class<?> type) {
        return resourceType().isAssignableFrom(type);
    }

    Decision decide(AccessSubject subject, R resource, String action);

    /**
     * Loads the resource for the {@code hasPermission(id, type, action)} form.
     * Empty when the policy does not support it, or the id names nothing the
     * caller can see — both evaluate as a refusal.
     */
    default Optional<R> load(Serializable id) {
        return Optional.empty();
    }
}
