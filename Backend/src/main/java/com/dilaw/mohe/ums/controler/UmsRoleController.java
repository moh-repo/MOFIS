package com.dilaw.mohe.ums.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.exception.ResourceNotFoundException;
import com.dilaw.mohe.ums.model.UmsRole;
import com.dilaw.mohe.ums.model.UmsPermission;
import com.dilaw.mohe.ums.service.UmsRoleService;
import com.dilaw.mohe.ums.util.UmsPageableUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * SEC-03: roles are global (no country), so every write is gated on the ministry-only
 * admin-tools codes, as pages, reports and API links are. A country admin holds
 * UMS_ADMIN.* for its own staff and used to rename or deactivate a global role.
 */
@RestController
@RequestMapping("api/v1/ums/roles")
@PreAuthorize(MoheSecurityExpressions.UMS_VIEW)
public class UmsRoleController {

    private static final Set<String> ROLE_SORT_FIELDS = Set.of(
            "id", "roleName", "roleNameAr", "description", "priority", "isActive", "isSystem", "createdAt");

    @Autowired
    private UmsRoleService roleService;

    @GetMapping("/")
    public ResponseEntity<Page<UmsRole>> getAllRoles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "roleName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean system) {
        Pageable pageable = UmsPageableUtil.of(page, size, sortBy, sortDir, ROLE_SORT_FIELDS, "roleName");
        return ResponseEntity.ok(roleService.listRoles(blankToNull(keyword), system, pageable));
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @GetMapping("/all")
    public ResponseEntity<List<UmsRole>> getAllActiveRoles() {
        return ResponseEntity.ok(roleService.getAllActiveRoles());
    }

    @GetMapping("/search")
    public ResponseEntity<Page<UmsRole>> searchRoles(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "roleName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) Boolean system) {
        Pageable pageable = UmsPageableUtil.of(page, size, sortBy, sortDir, ROLE_SORT_FIELDS, "roleName");
        return ResponseEntity.ok(roleService.listRoles(blankToNull(keyword), system, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UmsRole> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRoleById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id)));
    }

    @PostMapping("/")
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_ADD)
    public ResponseEntity<UmsRole> createRole(@Valid @RequestBody UmsRole role) {
        long createdBy = SecurityRequestUtils.requireUserId();
        return ResponseEntity.ok(roleService.createRole(role, createdBy));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_EDIT)
    public ResponseEntity<UmsRole> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UmsRole role) {
        long updatedBy = SecurityRequestUtils.requireUserId();
        return ResponseEntity.ok(roleService.updateRole(id, role, updatedBy));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_DELETE)
    public ResponseEntity<Map<String, String>> deleteRole(@PathVariable Long id) {
        long deletedBy = SecurityRequestUtils.requireUserId();
        roleService.deleteRole(id, deletedBy);
        return ResponseEntity.ok(Map.of("message", "Role deleted successfully"));
    }

    // ========== Permission Assignment ==========

    @GetMapping("/{id}/permissions")
    public ResponseEntity<List<UmsPermission>> getRolePermissions(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRolePermissions(id));
    }

    @PostMapping("/{id}/permissions/{permissionId}")
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_EDIT)
    public ResponseEntity<Map<String, String>> assignPermissionToRole(
            @PathVariable Long id,
            @PathVariable Long permissionId) {
        long grantedBy = SecurityRequestUtils.requireUserId();
        roleService.assignPermissionToRole(id, permissionId, grantedBy);
        return ResponseEntity.ok(Map.of("message", "Permission assigned to role successfully"));
    }

    @DeleteMapping("/{id}/permissions/{permissionId}")
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_EDIT)
    public ResponseEntity<Map<String, String>> removePermissionFromRole(
            @PathVariable Long id,
            @PathVariable Long permissionId) {
        long removedBy = SecurityRequestUtils.requireUserId();
        roleService.removePermissionFromRole(id, permissionId, removedBy);
        return ResponseEntity.ok(Map.of("message", "Permission removed from role successfully"));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_EDIT)
    public ResponseEntity<Map<String, String>> setRolePermissions(
            @PathVariable Long id,
            @Valid @RequestBody List<Long> permissionIds,
            @RequestParam(defaultValue = "false") boolean clearAll) {
        // An empty list strips the role of everything it holds. A screen whose
        // permission list failed to load sends exactly that, so it must be asked
        // for in so many words (QA B10, N12).
        if (permissionIds.isEmpty() && !clearAll) {
            throw new IllegalArgumentException(
                    "An empty permission list would remove every permission from the role; send clearAll=true to do that.");
        }
        long grantedBy = SecurityRequestUtils.requireUserId();
        roleService.setRolePermissions(id, permissionIds, grantedBy);
        return ResponseEntity.ok(Map.of("message", "Role permissions updated successfully"));
    }
}
