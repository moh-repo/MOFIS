package com.dilaw.mohe.security.policy;

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * {@code hasPermission(...)} in a {@code @PreAuthorize} expression, answered by
 * the policies (security plan R-16, decision D-5: in-process, no sidecar).
 *
 * <p>Three spellings:
 * <ul>
 *   <li>{@code hasPermission(#entity, 'EDIT')} — a loaded resource; the policy
 *       is found by its type;</li>
 *   <li>{@code hasPermission('Wafar', 'RECORD')} — a policy name as the target:
 *       a subject-only question, asked before any row is loaded;</li>
 *   <li>{@code hasPermission(#id, 'StudentDatum', 'EDIT')} — the policy loads
 *       the row; an id the caller cannot see evaluates false.</li>
 * </ul>
 *
 * <p>A false here becomes Spring's {@code AccessDeniedException}, which the
 * exception handler turns into the same 403 body and {@code ACCESS_DENIED}
 * audit row as a {@link PolicyDenied} from a service.
 */
@Component
public class MohePermissionEvaluator implements PermissionEvaluator {

    private final AccessControl accessControl;

    public MohePermissionEvaluator(AccessControl accessControl) {
        this.accessControl = accessControl;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object target, Object permission) {
        String action = String.valueOf(permission);
        if (target instanceof String policyName) {
            return accessControl.isAllowed(policyName, null, action);
        }
        if (target == null) return false;
        return accessControl.isAllowed(target, action);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        String action = String.valueOf(permission);
        AccessPolicy<Object> policy = (AccessPolicy<Object>) accessControl.policy(targetType).orElse(null);
        if (policy == null || targetId == null) return false;
        return policy.load(targetId)
                .map(resource -> accessControl.decideAs(accessControl.currentSubject(), targetType, resource, action).allowed())
                .orElse(false);
    }
}
