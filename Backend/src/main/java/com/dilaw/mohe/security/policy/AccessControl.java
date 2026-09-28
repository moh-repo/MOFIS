package com.dilaw.mohe.security.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The one place a service asks whether the caller may act (security plan R-16).
 *
 * <p>Finds the policy for the resource, resolves the caller, and turns a
 * refusal into a {@link PolicyDenied} (403, audited by the exception handler).
 * Services call {@link #assertAllowed} where they used to run their own
 * checks; controllers reach the same policies through
 * {@code @PreAuthorize("hasPermission(...)")} and {@link MohePermissionEvaluator}.
 *
 * <p>Constructed from the policy list so a unit test can build it with one
 * policy and a stubbed {@link AccessSubjectResolver}.
 */
@Service
public class AccessControl {

    private static final Logger log = LoggerFactory.getLogger(AccessControl.class);

    private final List<AccessPolicy<?>> policies;
    private final Map<String, AccessPolicy<?>> byName = new LinkedHashMap<>();
    private final AccessSubjectResolver subjects;

    public AccessControl(List<AccessPolicy<?>> policies, AccessSubjectResolver subjects) {
        this.policies = List.copyOf(policies);
        this.subjects = subjects;
        for (AccessPolicy<?> p : policies) {
            AccessPolicy<?> clash = byName.put(p.name(), p);
            if (clash != null) {
                throw new IllegalStateException("Two access policies are named " + p.name());
            }
        }
    }

    public List<String> policyNames() {
        return List.copyOf(byName.keySet());
    }

    public Optional<AccessPolicy<?>> policy(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /** The policy for a resource value, walking its type; none is a programming error, not a refusal. */
    public AccessPolicy<?> policyFor(Object resource) {
        if (resource == null) throw new IllegalArgumentException("A resource is required; name the policy for subject-only questions");
        for (AccessPolicy<?> p : policies) {
            if (p.handles(resource.getClass())) return p;
        }
        throw new IllegalStateException("No access policy judges " + resource.getClass().getName());
    }

    // ── Decide ───────────────────────────────────────────────────────────────

    public Decision decide(Object resource, String action) {
        return decideAs(subjects.current(), resource, action);
    }

    public Decision decide(String policyName, Object resourceOrNull, String action) {
        return decideAs(subjects.current(), policyName, resourceOrNull, action);
    }

    @SuppressWarnings("unchecked")
    public Decision decideAs(AccessSubject subject, Object resource, String action) {
        AccessPolicy<Object> policy = (AccessPolicy<Object>) policyFor(resource);
        return judge(policy, subject, resource, action);
    }

    @SuppressWarnings("unchecked")
    public Decision decideAs(AccessSubject subject, String policyName, Object resourceOrNull, String action) {
        AccessPolicy<Object> policy = (AccessPolicy<Object>) policy(policyName)
                .orElseThrow(() -> new IllegalStateException("No access policy named " + policyName));
        if (resourceOrNull != null && !policy.handles(resourceOrNull.getClass())) {
            throw new IllegalStateException("Policy " + policyName + " does not judge " + resourceOrNull.getClass().getName());
        }
        return judge(policy, subject, resourceOrNull, action);
    }

    private static Decision judge(AccessPolicy<Object> policy, AccessSubject subject, Object resource, String action) {
        Decision d = policy.decide(subject, resource, action);
        if (d == null) throw new IllegalStateException("Policy " + policy.name() + " returned no decision for " + action);
        if (log.isDebugEnabled()) {
            log.debug("policy={} action={} user={} -> {}{}", policy.name(), action, subject.userId(),
                    d.allowed() ? "allow" : "deny: " + d.reason(),
                    d.exempted() ? " via " + d.exemptions() : "");
        }
        return d;
    }

    // ── Assert ───────────────────────────────────────────────────────────────

    /** Refuses with 403 unless allowed; returns the decision so the caller can audit any exemption. */
    public Decision assertAllowed(Object resource, String action) {
        AccessPolicy<?> policy = policyFor(resource);
        Decision d = decide(resource, action);
        if (!d.allowed()) throw new PolicyDenied(policy.name(), action, d.reason());
        return d;
    }

    public Decision assertAllowed(String policyName, Object resourceOrNull, String action) {
        Decision d = decide(policyName, resourceOrNull, action);
        if (!d.allowed()) throw new PolicyDenied(policyName, action, d.reason());
        return d;
    }

    public boolean isAllowed(Object resource, String action) {
        return decide(resource, action).allowed();
    }

    public boolean isAllowed(String policyName, Object resourceOrNull, String action) {
        return decide(policyName, resourceOrNull, action).allowed();
    }

    public AccessSubject currentSubject() {
        return subjects.current();
    }
}
