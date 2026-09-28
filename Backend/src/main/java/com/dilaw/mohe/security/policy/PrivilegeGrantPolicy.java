package com.dilaw.mohe.security.policy;

import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.model.UmsGroup;
import com.dilaw.mohe.ums.model.UmsGroupRole;
import com.dilaw.mohe.ums.model.UmsPermission;
import com.dilaw.mohe.ums.model.UmsRole;
import com.dilaw.mohe.ums.repository.UmsGroupRoleRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Who may hand out what (N-02.b): a role, a group or a permission code.
 *
 * <p>Actions: {@code GRANT} on a {@link UmsRole}, {@link UmsGroup} or
 * {@link UmsPermission}; {@code EDIT} on a {@link UmsRole} (changing what it
 * carries). A super admin and a ministry admin grant freely, tier roles
 * excepted — nobody grants a tier above their own ({@link AdminTier#mayGrant}).
 * Anyone else may grant only what they hold themselves, so a country admin
 * cannot escalate by proxy: no {@code MINISTRY_*}, no {@code SYSTEM.RESTORE},
 * no {@code *.APPROVE} it does not carry. A group is granted through its roles,
 * every one of which must be grantable. The bundle of a tier role is changed
 * by a super admin only.
 */
@Component
public class PrivilegeGrantPolicy implements AccessPolicy<Object> {

    public static final String NAME = "PrivilegeGrant";

    private final UmsGroupRoleRepository groupRoleRepository;

    public PrivilegeGrantPolicy(UmsGroupRoleRepository groupRoleRepository) {
        this.groupRoleRepository = groupRoleRepository;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<Object> resourceType() {
        return Object.class;
    }

    @Override
    public boolean handles(Class<?> type) {
        return UmsRole.class.isAssignableFrom(type) || UmsGroup.class.isAssignableFrom(type)
                || UmsPermission.class.isAssignableFrom(type);
    }

    @Override
    public Decision decide(AccessSubject subject, Object resource, String action) {
        if (!subject.isAuthenticated()) return Decision.deny("Access denied: not signed in");
        if (resource == null) return Decision.deny("Nothing to grant.");
        if (resource instanceof UmsRole role) {
            return "EDIT".equals(action) ? editRole(subject, role) : grantRole(subject, role);
        }
        if (resource instanceof UmsGroup group) {
            for (UmsGroupRole gr : groupRoleRepository.findByGroupIdAndIsActiveTrue(group.getId())) {
                Decision d = grantRole(subject, gr.getRole());
                if (!d.allowed()) return d;
            }
            return Decision.allow();
        }
        if (resource instanceof UmsPermission permission) {
            return grantPermission(subject, permission.getPermissionCode());
        }
        return Decision.deny("Access denied.");
    }

    public Decision grantRole(AccessSubject subject, UmsRole role) {
        AdminTier caller = subject.tier();
        Optional<AdminTier> conferred = AdminTier.fromRoleName(role.getRoleName());
        if (conferred.isPresent()) {
            return caller.mayGrant(conferred.get()) ? Decision.allow()
                    : Decision.deny("The " + role.getRoleName() + " tier can only be granted or removed by a higher tier.");
        }
        if (caller == AdminTier.SUPER_ADMIN || caller == AdminTier.MINISTRY_ADMIN) return Decision.allow();
        if (subject.hasRole(role.getRoleName())) return Decision.allow();
        return Decision.deny("You may grant only roles you hold yourself (" + role.getRoleName() + ").");
    }

    public Decision grantPermission(AccessSubject subject, String code) {
        AdminTier caller = subject.tier();
        if (caller == AdminTier.SUPER_ADMIN || caller == AdminTier.MINISTRY_ADMIN) return Decision.allow();
        if (subject.hasCode(code)) return Decision.allow();
        return Decision.deny("You may grant only permissions you hold yourself (" + code + ").");
    }

    private static Decision editRole(AccessSubject subject, UmsRole role) {
        if (AdminTier.fromRoleName(role.getRoleName()).isPresent() && subject.tier() != AdminTier.SUPER_ADMIN) {
            return Decision.deny("The permission bundle of a tier role is changed by a super administrator only.");
        }
        return Decision.allow();
    }
}
