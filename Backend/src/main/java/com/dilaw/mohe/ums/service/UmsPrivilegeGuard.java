package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.exception.ResourceNotFoundException;
import com.dilaw.mohe.security.policy.AccessControl;
import com.dilaw.mohe.security.policy.Decision;
import com.dilaw.mohe.security.policy.PolicyDenied;
import com.dilaw.mohe.security.policy.PrivilegeGrantPolicy;
import com.dilaw.mohe.security.policy.UmsUserPolicy;
import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.model.UmsGroup;
import com.dilaw.mohe.ums.model.UmsPermission;
import com.dilaw.mohe.ums.model.UmsRole;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.repository.UmsGroupRepository;
import com.dilaw.mohe.ums.repository.UmsPermissionRepository;
import com.dilaw.mohe.ums.repository.UmsRoleRepository;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Who may act on whom, and who may hand out what.
 *
 * <p>Since the R-16 pass (6 Sep 2026) every rule lives in
 * {@link UmsUserPolicy} and {@link PrivilegeGrantPolicy}; this class is the
 * adapter the user service and its tests have always called, loading the
 * target row and asking {@link AccessControl}. It exists so that the callers
 * did not have to change with the rules — the rules are the policies.
 *
 * <p>A target that does not exist passes here: the caller's own not-found
 * handling applies, and a 404 from a guard would read as the account having
 * vanished.
 */
@Service
public class UmsPrivilegeGuard {

    @Autowired private UmsUserRepository userRepository;
    @Autowired private UmsRoleRepository roleRepository;
    @Autowired private UmsGroupRepository groupRepository;
    @Autowired private UmsPermissionRepository permissionRepository;
    @Autowired private CountryScopeService countryScopeService;
    @Autowired private AdminTierService adminTierService;
    @Autowired private AccessControl accessControl;

    /** Refuses any write against an account the caller's tier may not administer (N-01, N-02). */
    public void assertMayAdminister(Long targetId) {
        if (targetId == null) return;
        Long callerId = callerId();
        if (callerId == null) return; // no caller: the security chain has already decided
        UmsUser target = userRepository.findById(targetId).orElse(null);
        if (target == null) return;
        accessControl.assertAllowed(target, "ADMINISTER");
    }

    /** Kept for callers of the phase-1 name; the tier matrix covers it. */
    public void assertNotMinistryStaffActingOnOthers(Long targetId) {
        assertMayAdminister(targetId);
    }

    /** Refuses account creation to any tier that does not administer staff (N-01, N-02). */
    public void assertMayCreateStaff() {
        accessControl.assertAllowed(UmsUserPolicy.NAME, null, "CREATE");
    }

    /** Refuses granting the administrator flag to anyone who does not hold an administering tier. */
    public void assertMayGrantAdmin(Boolean requestedIsAdmin) {
        if (!Boolean.TRUE.equals(requestedIsAdmin)) return;
        accessControl.assertAllowed(UmsUserPolicy.NAME, null, "GRANT_ADMIN");
    }

    /** N-02.b: a role may be handed out only by a tier that may grant it, or by someone who holds it. */
    public void assertMayGrantRole(Long roleId) {
        UmsRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
        accessControl.assertAllowed(role, "GRANT");
    }

    /** A group is granted through its roles: every one of them must be grantable by the caller. */
    public void assertMayGrantGroup(Long groupId) {
        UmsGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
        accessControl.assertAllowed(group, "GRANT");
    }

    /** N-02.b: a direct grant of a code the caller does not hold is a privilege escalation by proxy. */
    public void assertMayGrantPermission(String permissionCode) {
        UmsPermission permission = permissionRepository.findByPermissionCode(permissionCode)
                .orElseGet(() -> {
                    UmsPermission p = new UmsPermission();
                    p.setPermissionCode(permissionCode);
                    return p;
                });
        accessControl.assertAllowed(permission, "GRANT");
    }

    /** Only a super admin changes what a tier role carries. */
    public void assertMayEditRole(Long roleId) {
        UmsRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
        accessControl.assertAllowed(role, "EDIT");
    }

    /** Refuses self-deletion: the one delete with no way back. */
    public void assertNotSelf(Long targetId) {
        Long callerId = callerId();
        if (callerId != null && callerId.equals(targetId)) {
            throw new PolicyDenied(UmsUserPolicy.NAME, "DELETE", "You cannot delete your own account.");
        }
    }

    /** The decision without the throw, for callers that route a refusal elsewhere. */
    public Decision mayGrantRole(Long roleId) {
        UmsRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
        return accessControl.decide(role, "GRANT");
    }

    public AdminTier callerTier() {
        return adminTierService.tierOf(callerId());
    }

    private Long callerId() {
        return countryScopeService.getScope().getUserId();
    }
}
