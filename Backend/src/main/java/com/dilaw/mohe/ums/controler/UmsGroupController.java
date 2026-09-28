package com.dilaw.mohe.ums.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.exception.ResourceNotFoundException;
import com.dilaw.mohe.ums.model.UmsGroup;
import com.dilaw.mohe.ums.model.UmsRole;
import com.dilaw.mohe.ums.service.UmsGroupService;
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
@RequestMapping("api/v1/ums/groups")
@PreAuthorize(MoheSecurityExpressions.UMS_VIEW)
public class UmsGroupController {

    private static final Set<String> GROUP_SORT_FIELDS = Set.of(
            "id", "groupName", "groupNameAr", "description", "isActive", "isSystem", "createdAt");

    @Autowired
    private UmsGroupService groupService;

    @GetMapping("/")
    public ResponseEntity<Page<UmsGroup>> getAllGroups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "groupName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean system) {
        Pageable pageable = UmsPageableUtil.of(page, size, sortBy, sortDir, GROUP_SORT_FIELDS, "groupName");
        return ResponseEntity.ok(groupService.listGroups(blankToNull(keyword), system, pageable));
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @GetMapping("/all")
    public ResponseEntity<List<UmsGroup>> getAllActiveGroups() {
        return ResponseEntity.ok(groupService.getAllActiveGroups());
    }

    @GetMapping("/search")
    public ResponseEntity<Page<UmsGroup>> searchGroups(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "groupName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) Boolean system) {
        Pageable pageable = UmsPageableUtil.of(page, size, sortBy, sortDir, GROUP_SORT_FIELDS, "groupName");
        return ResponseEntity.ok(groupService.listGroups(blankToNull(keyword), system, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UmsGroup> getGroupById(@PathVariable Long id) {
        return ResponseEntity.ok(groupService.getGroupById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + id)));
    }

    @PostMapping("/")
    @PreAuthorize(MoheSecurityExpressions.UMS_ADD)
    public ResponseEntity<UmsGroup> createGroup(@Valid @RequestBody UmsGroup group) {
        long createdBy = SecurityRequestUtils.requireUserId();
        return ResponseEntity.ok(groupService.createGroup(group, createdBy));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<UmsGroup> updateGroup(
            @PathVariable Long id,
            @Valid @RequestBody UmsGroup group) {
        long updatedBy = SecurityRequestUtils.requireUserId();
        return ResponseEntity.ok(groupService.updateGroup(id, group, updatedBy));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.UMS_DELETE)
    public ResponseEntity<Map<String, String>> deleteGroup(@PathVariable Long id) {
        long deletedBy = SecurityRequestUtils.requireUserId();
        groupService.deleteGroup(id, deletedBy);
        return ResponseEntity.ok(Map.of("message", "Group deleted successfully"));
    }

    // ========== Role Assignment ==========

    @GetMapping("/{id}/roles")
    public ResponseEntity<List<UmsRole>> getGroupRoles(@PathVariable Long id) {
        return ResponseEntity.ok(groupService.getGroupRoles(id));
    }

    @PostMapping("/{id}/roles/{roleId}")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Map<String, String>> assignRoleToGroup(
            @PathVariable Long id,
            @PathVariable Long roleId) {
        long assignedBy = SecurityRequestUtils.requireUserId();
        groupService.assignRoleToGroup(id, roleId, assignedBy);
        return ResponseEntity.ok(Map.of("message", "Role assigned to group successfully"));
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Map<String, String>> removeRoleFromGroup(
            @PathVariable Long id,
            @PathVariable Long roleId) {
        long removedBy = SecurityRequestUtils.requireUserId();
        groupService.removeRoleFromGroup(id, roleId, removedBy);
        return ResponseEntity.ok(Map.of("message", "Role removed from group successfully"));
    }
}
