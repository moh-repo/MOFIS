package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.model.UmsGroupRole;
import com.dilaw.mohe.ums.model.UmsPermission;
import com.dilaw.mohe.ums.model.UmsPermissionConflict;
import com.dilaw.mohe.ums.repository.UmsGroupRoleRepository;
import com.dilaw.mohe.ums.repository.UmsPermissionConflictRepository;
import com.dilaw.mohe.ums.repository.UmsPermissionRepository;
import com.dilaw.mohe.ums.repository.UmsRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Separation of duties: refuses an assignment whose result would put both
 * halves of a maker/checker pair on one person.
 *
 * <p>Security plan R-15 (6 Sep 2026). The pairs are rows of
 * {@code UMSCORE.PermissionConflicts} ({@link UmsPermissionConflict}); the
 * check is on the <em>effective</em> permission set — roles, groups and direct
 * grants together — so a group is checked through its roles and a role through
 * its permissions. Only violations the assignment would <em>introduce</em> are
 * refused: an account that already holds a pair (the migration lists them)
 * is not blocked from receiving an unrelated role, and administrators resolve
 * the existing ones in their own time.
 *
 * <p>The super admin is exempt, as everywhere else in the plan: the one
 * account that may do both is the one whose every bypass is audited
 * (decisions D-12, D-13).
 */
@Service
public class SeparationOfDutiesService {

    /** One breached pair. */
    public record Violation(String codeA, String codeB, String reason) {
        public String describe() {
            return codeA + " with " + codeB + (reason == null || reason.isBlank() ? "" : " (" + reason + ")");
        }
    }

    @Autowired private UmsPermissionConflictRepository conflictRepository;
    @Autowired private UmsPermissionRepository permissionRepository;
    @Autowired private UmsGroupRoleRepository groupRoleRepository;
    @Autowired private UmsRoleRepository roleRepository;
    @Autowired private UmsPermissionService permissionService;
    @Autowired private AdminTierService adminTierService;

    public List<UmsPermissionConflict> activeConflicts() {
        return conflictRepository.findByIsActiveTrue();
    }

    /** The pairs breached by this set of codes. */
    public List<Violation> violationsIn(Collection<String> codes) {
        Set<String> held = new HashSet<>(codes);
        List<Violation> out = new ArrayList<>();
        for (UmsPermissionConflict c : activeConflicts()) {
            if (held.contains(c.getCodeA()) && held.contains(c.getCodeB())) {
                out.add(new Violation(c.getCodeA(), c.getCodeB(), c.getReason()));
            }
        }
        return out;
    }

    public Set<String> codesOfRole(Long roleId) {
        Set<String> codes = new LinkedHashSet<>();
        for (UmsPermission p : permissionRepository.findPermissionsByRoleId(roleId)) codes.add(p.getPermissionCode());
        return codes;
    }

    public Set<String> codesOfGroup(Long groupId) {
        Set<String> codes = new LinkedHashSet<>();
        for (UmsGroupRole gr : groupRoleRepository.findByGroupIdAndIsActiveTrue(groupId)) {
            codes.addAll(codesOfRole(gr.getRole().getId()));
        }
        return codes;
    }

    /** What the account holds today, every source together. */
    public Set<String> effectiveCodes(Long userId) {
        return new LinkedHashSet<>(permissionService.getUserPermissionCodes(userId));
    }

    /**
     * Refuses with 409 when adding {@code added} to the account would breach a
     * pair it does not already breach.
     *
     * @param what how the codes arrive, for the message: {@code "role Finance Manager"}
     */
    public void assertUserMayReceive(Long userId, Collection<String> added, String what) {
        if (adminTierService.tierOf(userId) == AdminTier.SUPER_ADMIN) return;
        Set<String> before = effectiveCodes(userId);
        List<Violation> already = violationsIn(before);
        Set<String> after = new HashSet<>(before);
        after.addAll(added);
        List<Violation> introduced = new ArrayList<>(violationsIn(after));
        introduced.removeAll(already);
        if (!introduced.isEmpty()) {
            throw conflict("Assigning " + what + " would give this account both halves of a separated duty: "
                    + describe(introduced) + ". Split the duties across two people.");
        }
    }

    /** Refuses with 409 when a role would itself carry both halves of a pair. */
    public void assertRoleMayHold(Long roleId, Collection<String> codes) {
        boolean superAdminRole = roleRepository.findById(roleId)
                .map(r -> AdminTier.SUPER_ADMIN.name().equals(r.getRoleName()))
                .orElse(false);
        if (superAdminRole) return;
        List<Violation> violations = violationsIn(codes);
        if (!violations.isEmpty()) {
            throw conflict("A role may not carry both halves of a separated duty: " + describe(violations)
                    + ". Put the two halves on different roles.");
        }
    }

    /** Refuses with 409 when adding a role to a group would make the group's bundle breach a pair. */
    public void assertGroupMayHold(Long groupId, Long roleId) {
        Set<String> after = codesOfGroup(groupId);
        List<Violation> already = violationsIn(after);
        after.addAll(codesOfRole(roleId));
        List<Violation> introduced = new ArrayList<>(violationsIn(after));
        introduced.removeAll(already);
        if (!introduced.isEmpty()) {
            throw conflict("Adding this role would give the group both halves of a separated duty: "
                    + describe(introduced) + ".");
        }
    }

    private static String describe(List<Violation> violations) {
        return String.join("; ", violations.stream().map(Violation::describe).toList());
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
