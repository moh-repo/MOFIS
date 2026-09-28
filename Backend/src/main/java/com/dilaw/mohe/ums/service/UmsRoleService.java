package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.exception.ResourceNotFoundException;
import com.dilaw.mohe.ums.model.*;
import com.dilaw.mohe.ums.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.dilaw.mohe.exception.LocalizedStateException;

@Service
public class UmsRoleService {

    @Autowired
    private UmsRoleRepository roleRepository;

    @Autowired
    private UmsRolePermissionRepository rolePermissionRepository;

    @Autowired
    private UmsPermissionRepository permissionRepository;

    @Autowired
    private UmsAuditLogService auditLogService;

    /** R-19: the permissions of a role feed the authorities of every holder. */
    @Autowired
    private UserAuthorityCache authorityCache;

    /** R-15: a role may not carry both halves of a separated duty. */
    @Autowired
    private SeparationOfDutiesService separationOfDuties;

    /** R-12: the bundle of a tier role is changed by a super admin only. */
    @Autowired
    private UmsPrivilegeGuard privilegeGuard;

    // ========== Role CRUD ==========

    public List<UmsRole> getAllActiveRoles() {
        return roleRepository.findByIsActiveTrueOrderByPriorityDesc();
    }

    public Page<UmsRole> listRoles(String keyword, Boolean isSystem, Pageable pageable) {
        return roleRepository.listRoles(trimNull(keyword), isSystem, pageable);
    }

    private static String trimNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    public Page<UmsRole> getAllRoles(Pageable pageable) {
        return listRoles(null, null, pageable);
    }

    public Page<UmsRole> searchRoles(String keyword, Pageable pageable) {
        return roleRepository.searchRoles(keyword, pageable);
    }

    public Optional<UmsRole> getRoleById(Long id) {
        return roleRepository.findById(id);
    }

    public Optional<UmsRole> getRoleByName(String roleName) {
        return roleRepository.findByRoleName(roleName);
    }

    @Transactional
    public UmsRole createRole(UmsRole role, Long createdBy) {
        // SEC-01: never merge onto an existing role (renaming it to a tier role, say).
        role.setId(null);
        assertNotTierRoleName(role.getRoleName());
        if (roleRepository.existsByRoleName(role.getRoleName())) {
            throw new IllegalArgumentException("Role name already exists: " + role.getRoleName());
        }

        role.setCreatedBy(createdBy);
        role.setCreatedAt(LocalDateTime.now());
        UmsRole savedRole = roleRepository.save(role);

        auditLogService.log(createdBy, "CREATE_ROLE", "ROLE", savedRole.getId(), null, role.getRoleName());

        return savedRole;
    }

    @Transactional
    public UmsRole updateRole(Long id, UmsRole roleUpdate, Long updatedBy) {
        UmsRole existingRole = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));

        if (existingRole.getIsSystem()) {
            // 409, not 400 (review M9): the request is well formed; the row is the one that refuses it.
            throw new LocalizedStateException("A system role cannot be edited or deleted.", "ums.role.system");
        }
        // SEC-03: the same rule as a change to the role's permission set.
        privilegeGuard.assertMayEditRole(id);

        String oldValue = existingRole.getRoleName();

        if (roleUpdate.getRoleName() != null && !roleUpdate.getRoleName().equals(oldValue)) {
            // SEC-03: a role renamed to MINISTRY_ADMIN would confer that tier on every holder.
            assertNotTierRoleName(roleUpdate.getRoleName());
            existingRole.setRoleName(roleUpdate.getRoleName());
        }
        if (roleUpdate.getRoleNameAr() != null) {
            existingRole.setRoleNameAr(roleUpdate.getRoleNameAr());
        }
        if (roleUpdate.getDescription() != null) {
            existingRole.setDescription(roleUpdate.getDescription());
        }
        if (roleUpdate.getDescriptionAr() != null) {
            existingRole.setDescriptionAr(roleUpdate.getDescriptionAr());
        }
        if (roleUpdate.getPriority() != null) {
            existingRole.setPriority(roleUpdate.getPriority());
        }
        if (roleUpdate.getIsActive() != null) {
            existingRole.setIsActive(roleUpdate.getIsActive());
        }

        existingRole.setUpdatedBy(updatedBy);
        existingRole.setUpdatedAt(LocalDateTime.now());

        UmsRole savedRole = roleRepository.save(existingRole);
        // SEC-03: a rename or a deactivation changes what every holder is.
        authorityCache.evictAll();

        auditLogService.log(updatedBy, "UPDATE_ROLE", "ROLE", id, oldValue, savedRole.getRoleName());

        return savedRole;
    }

    @Transactional
    public void deleteRole(Long id, Long deletedBy) {
        UmsRole role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));

        if (role.getIsSystem()) {
            throw new LocalizedStateException("A system role cannot be edited or deleted.", "ums.role.system");
        }
        privilegeGuard.assertMayEditRole(id); // SEC-03

        role.setIsActive(false);
        role.setUpdatedBy(deletedBy);
        roleRepository.save(role);
        authorityCache.evictAll();

        auditLogService.log(deletedBy, "DELETE_ROLE", "ROLE", id, role.getRoleName(), null);
    }

    /**
     * SEC-03: the tier roles ({@link AdminTier}) are conferred by name, so no
     * other role may take one of those names, whatever its case or padding
     * (the database collation compares names case-insensitively).
     */
    static void assertNotTierRoleName(String roleName) {
        if (roleName == null) return;
        String normalised = roleName.strip().toUpperCase(java.util.Locale.ROOT);
        if (AdminTier.fromRoleName(normalised).isPresent()) {
            throw new com.dilaw.mohe.exception.LocalizedArgumentException(
                    "The role name " + roleName.strip() + " is reserved for an administrative tier.",
                    "ums.role.name.reserved", roleName.strip());
        }
    }

    // ========== Permission Assignment to Role ==========

    @Transactional
    public void assignPermissionToRole(Long roleId, Long permissionId, Long grantedBy) {
        privilegeGuard.assertMayEditRole(roleId);
        if (rolePermissionRepository.existsByRoleIdAndPermissionIdAndIsActiveTrue(roleId, permissionId)) {
            throw new IllegalArgumentException("Permission already assigned to role");
        }

        UmsRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
        UmsPermission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + permissionId));
        java.util.Set<String> after = separationOfDuties.codesOfRole(roleId);
        after.add(permission.getPermissionCode());
        separationOfDuties.assertRoleMayHold(roleId, after);

        UmsRolePermission rolePermission = new UmsRolePermission();
        rolePermission.setRole(role);
        rolePermission.setPermission(permission);
        rolePermission.setGrantedBy(grantedBy);
        rolePermission.setIsActive(true);
        rolePermissionRepository.save(rolePermission);
        authorityCache.evictAll();

        auditLogService.log(grantedBy, "ASSIGN_ROLE_PERMISSION", "ROLE_PERMISSION", roleId, null,
                permission.getPermissionCode());
    }

    @Transactional
    public void removePermissionFromRole(Long roleId, Long permissionId, Long removedBy) {
        privilegeGuard.assertMayEditRole(roleId);
        UmsRolePermission rolePermission = rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Role-Permission relationship not found"));

        rolePermission.setIsActive(false);
        rolePermissionRepository.save(rolePermission);
        authorityCache.evictAll();

        auditLogService.log(removedBy, "REMOVE_ROLE_PERMISSION", "ROLE_PERMISSION", roleId,
                rolePermission.getPermission().getPermissionCode(), null);
    }

    @Transactional
    public void setRolePermissions(Long roleId, List<Long> permissionIds, Long grantedBy) {
        privilegeGuard.assertMayEditRole(roleId);
        // R-15: judged on the set being installed, before anything is touched.
        java.util.Set<String> codes = new java.util.LinkedHashSet<>();
        for (UmsPermission p : permissionRepository.findAllById(permissionIds)) codes.add(p.getPermissionCode());
        separationOfDuties.assertRoleMayHold(roleId, codes);
        // Deactivate all existing permissions
        List<UmsRolePermission> existing = rolePermissionRepository.findByRoleIdAndIsActiveTrue(roleId);
        existing.forEach(rp -> rp.setIsActive(false));
        rolePermissionRepository.saveAll(existing);

        // Add new permissions. One fetch for all ids and one batched save: a role with
        // sixty permissions used to cost sixty SELECTs and sixty INSERT round trips.
        UmsRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));

        Map<Long, UmsPermission> permissionsById = new HashMap<>();
        for (UmsPermission p : permissionRepository.findAllById(permissionIds)) {
            permissionsById.put(p.getId(), p);
        }
        List<UmsRolePermission> grants = new ArrayList<>(permissionIds.size());
        for (Long permissionId : permissionIds) {
            // Same failure as before for an unknown id: the first one in request order.
            UmsPermission permission = permissionsById.get(permissionId);
            if (permission == null) {
                throw new ResourceNotFoundException("Permission not found: " + permissionId);
            }
            UmsRolePermission rolePermission = new UmsRolePermission();
            rolePermission.setRole(role);
            rolePermission.setPermission(permission);
            rolePermission.setGrantedBy(grantedBy);
            rolePermission.setIsActive(true);
            grants.add(rolePermission);
        }
        rolePermissionRepository.saveAll(grants);
        authorityCache.evictAll();

        auditLogService.log(grantedBy, "SET_ROLE_PERMISSIONS", "ROLE", roleId, null,
                permissionIds.size() + " permissions");
    }

    public List<UmsPermission> getRolePermissions(Long roleId) {
        return permissionRepository.findPermissionsByRoleId(roleId);
    }
}
