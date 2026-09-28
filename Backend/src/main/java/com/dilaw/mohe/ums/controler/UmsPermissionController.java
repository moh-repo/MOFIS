package com.dilaw.mohe.ums.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.ums.model.*;
import com.dilaw.mohe.ums.service.UmsPermissionService;
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

@RestController
@RequestMapping("api/v1/ums/permissions")
@PreAuthorize(MoheSecurityExpressions.UMS_VIEW)
public class UmsPermissionController {

    private static final Set<String> PERMISSION_SORT_FIELDS = Set.of(
            "id", "permissionCode", "permissionName", "permissionNameAr",
            "resourceType", "actionType", "createdAt");

    @Autowired
    private UmsPermissionService permissionService;

    // ========== Permission CRUD ==========

    @GetMapping("/")
    public ResponseEntity<Page<UmsPermission>> getAllPermissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "permissionCode") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String actionType) {
        Pageable pageable = UmsPageableUtil.of(page, size, sortBy, sortDir, PERMISSION_SORT_FIELDS,
                "permissionCode");
        return ResponseEntity.ok(permissionService.listPermissions(blankToNull(keyword),
                blankToNull(resourceType), blankToNull(actionType), pageable));
    }

    @GetMapping("/all")
    public ResponseEntity<List<UmsPermission>> getAllActivePermissions() {
        return ResponseEntity.ok(permissionService.getAllActivePermissions());
    }

    @GetMapping("/search")
    public ResponseEntity<Page<UmsPermission>> searchPermissions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "permissionCode") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String actionType) {
        Pageable pageable = UmsPageableUtil.of(page, size, sortBy, sortDir, PERMISSION_SORT_FIELDS,
                "permissionCode");
        return ResponseEntity.ok(permissionService.listPermissions(blankToNull(keyword),
                blankToNull(resourceType), blankToNull(actionType), pageable));
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @GetMapping("/resource-type/{resourceType}")
    public ResponseEntity<List<UmsPermission>> getByResourceType(@PathVariable String resourceType) {
        return ResponseEntity.ok(permissionService.getPermissionsByResourceType(resourceType));
    }

    @GetMapping("/action-type/{actionType}")
    public ResponseEntity<List<UmsPermission>> getByActionType(@PathVariable String actionType) {
        return ResponseEntity.ok(permissionService.getPermissionsByActionType(actionType));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UmsPermission> getPermissionById(@PathVariable Long id) {
        return permissionService.getPermissionById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize(MoheSecurityExpressions.UMS_ADD)
    public ResponseEntity<UmsPermission> createPermission(@Valid @RequestBody UmsPermission permission) {
        return ResponseEntity.ok(permissionService.createPermission(permission));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<UmsPermission> updatePermission(@PathVariable Long id,
            @Valid @RequestBody UmsPermission permission) {
        return ResponseEntity.ok(permissionService.updatePermission(id, permission));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.UMS_DELETE)
    public ResponseEntity<Void> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return ResponseEntity.noContent().build();
    }

    // ========== Permission Checking ==========

    @GetMapping("/check")
    public ResponseEntity<Map<String, Boolean>> checkPermission(
            @RequestParam Long userId,
            @RequestParam String permissionCode) {
        boolean hasPermission = permissionService.hasPermission(userId, permissionCode);
        return ResponseEntity.ok(Map.of("hasPermission", hasPermission));
    }

    @GetMapping("/check/page")
    public ResponseEntity<Map<String, Boolean>> checkPageAccess(
            @RequestParam Long userId,
            @RequestParam String pageCode) {
        boolean canAccess = permissionService.canAccessPage(userId, pageCode);
        return ResponseEntity.ok(Map.of("canAccess", canAccess));
    }

    @GetMapping("/check/page-action")
    public ResponseEntity<Map<String, Boolean>> checkPageAction(
            @RequestParam Long userId,
            @RequestParam String pageCode,
            @RequestParam String actionType) {
        boolean canPerform = permissionService.canPerformPageAction(userId, pageCode, actionType);
        return ResponseEntity.ok(Map.of("canPerform", canPerform));
    }

    // ========== User Permissions ==========

    @GetMapping("/user/{userId}")
    public ResponseEntity<Set<UmsPermission>> getUserPermissions(@PathVariable Long userId) {
        return ResponseEntity.ok(permissionService.getUserPermissions(userId));
    }

    @GetMapping("/user/{userId}/codes")
    public ResponseEntity<List<String>> getUserPermissionCodes(@PathVariable Long userId) {
        return ResponseEntity.ok(permissionService.getUserPermissionCodes(userId));
    }

    @GetMapping("/user/{userId}/map")
    public ResponseEntity<Map<String, List<String>>> getUserPermissionMap(@PathVariable Long userId) {
        return ResponseEntity.ok(permissionService.getPermissionMap(userId));
    }

    // ========== Accessible Resources ==========

    @GetMapping("/user/{userId}/pages")
    public ResponseEntity<List<UmsPage>> getAccessiblePages(@PathVariable Long userId) {
        return ResponseEntity.ok(permissionService.getAccessiblePages(userId));
    }

    @GetMapping("/user/{userId}/fields")
    public ResponseEntity<List<UmsField>> getAccessibleFields(
            @PathVariable Long userId,
            @RequestParam Long pageId,
            @RequestParam(defaultValue = "VIEW") String actionType) {
        return ResponseEntity.ok(permissionService.getAccessibleFields(userId, pageId, actionType));
    }

    @GetMapping("/user/{userId}/field-codes")
    public ResponseEntity<List<String>> getAccessibleFieldCodes(
            @PathVariable Long userId,
            @RequestParam String pageCode,
            @RequestParam(defaultValue = "VIEW") String actionType) {
        return ResponseEntity.ok(permissionService.getAccessibleFieldCodes(userId, pageCode, actionType));
    }

    @GetMapping("/user/{userId}/reports")
    public ResponseEntity<List<UmsReport>> getAccessibleReports(@PathVariable Long userId) {
        return ResponseEntity.ok(permissionService.getAccessibleReports(userId));
    }
}
