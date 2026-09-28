package com.dilaw.mohe.ums.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.exception.ResourceNotFoundException;
import com.dilaw.mohe.ums.dto.UmsUserOptionDto;
import com.dilaw.mohe.ums.dto.UmsUserSummaryDto;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.model.UmsGroup;
import com.dilaw.mohe.ums.model.UmsRole;
import com.dilaw.mohe.ums.model.UmsUserCountry;
import com.dilaw.mohe.ums.model.UmsPermission;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.dilaw.mohe.ums.service.UmsUserService;
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
@RequestMapping("api/v1/ums/users")
@PreAuthorize(MoheSecurityExpressions.UMS_VIEW)
public class UmsUserController {

    private static final Set<String> USER_SORT_FIELDS = Set.of(
            "id", "userName", "email", "firstName", "lastName", "status",
            "lastLoginAt", "createdAt");

    @Autowired
    private UmsUserService userService;

    @Autowired
    private com.dilaw.mohe.ums.service.MfaService mfaService;

    @Autowired
    private UmsUserRepository userRepository;

    /**
     * The caller's own account, in the same shape as {@code GET /{id}}.
     *
     * <p>The profile page loaded {@code /{id}} with the caller's own id and 403'd for
     * everyone without {@code UMS.VIEW} — that is, for every non-administrator. Reading
     * one's own record needs no administrative permission, so the method-level gate
     * replaces the class-level one. Read straight from the repository rather than
     * through {@code UmsUserService.getUserById}: that lookup is scoped to the accounts
     * the caller may <em>administer</em>, which for a plain user is none.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UmsUser> getCurrentUser() {
        long id = SecurityRequestUtils.requireUserId();
        return ResponseEntity.ok(userRepository.findById(id)
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id)));
    }

    // ========== User CRUD ==========

    @GetMapping("/")
    @PreAuthorize("hasAuthority('UMS_ADMIN.VIEW') or hasAuthority('UMS.VIEW') or hasAuthority('UMS_USERS.VIEW')")
    public ResponseEntity<Page<UmsUserSummaryDto>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "userName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String privilege) {
        Pageable pageable = UmsPageableUtil.of(page, size, sortBy, sortDir, USER_SORT_FIELDS, "userName");
        // A summary per row (W5-01, H5a): the full record is read by id.
        return ResponseEntity.ok(userService.listUsers(blankToNull(keyword), blankToNull(status), normalizePrivilege(privilege),
                pageable).map(UmsUserSummaryDto::of));
    }

    @GetMapping("/search")
    @PreAuthorize(MoheSecurityExpressions.UMS_VIEW)
    public ResponseEntity<Page<UmsUserSummaryDto>> searchUsers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "userName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String privilege) {
        Pageable pageable = UmsPageableUtil.of(page, size, sortBy, sortDir, USER_SORT_FIELDS, "userName");
        // A summary per row (W5-01, H5a): the full record is read by id.
        return ResponseEntity.ok(userService.listUsers(blankToNull(keyword), blankToNull(status), normalizePrivilege(privilege),
                pageable).map(UmsUserSummaryDto::of));
    }

    @GetMapping("/admins")
    public ResponseEntity<Page<UmsUserSummaryDto>> getAdminUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "userName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Pageable pageable = UmsPageableUtil.of(page, size, sortBy, sortDir, USER_SORT_FIELDS, "userName");
        return ResponseEntity.ok(userService.getAdminUsers(pageable).map(UmsUserSummaryDto::of));
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** ADMIN | REGULAR | null ; ALL interpreted as null. */
    private static String normalizePrivilege(String privilege) {
        String p = blankToNull(privilege);
        if (p == null) {
            return null;
        }
        String u = p.toUpperCase();
        if ("ALL".equals(u)) {
            return null;
        }
        return ("ADMIN".equals(u) || "REGULAR".equals(u)) ? u : null;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('UMS_ADMIN.VIEW') or hasAuthority('UMS.VIEW') or hasAuthority('UMS_USERS.VIEW')")
    public ResponseEntity<UmsUser> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserDetail(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id)));
    }

    @PostMapping("/")
    @PreAuthorize(MoheSecurityExpressions.UMS_ADD)
    public ResponseEntity<UmsUser> createUser(@Valid @RequestBody UmsUser user) {
        long createdBy = SecurityRequestUtils.requireUserId();
        return ResponseEntity.ok(userService.createUser(user, createdBy));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<UmsUser> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UmsUser user) {
        long updatedBy = SecurityRequestUtils.requireUserId();
        // S-03: a promotion to administrator is requested, not applied (202 + pendingApprovalRequestId).
        UmsUser saved = userService.updateUserOrRequest(id, user, updatedBy);
        return saved.getPendingApprovalRequestId() != null
                ? ResponseEntity.accepted().body(saved)
                : ResponseEntity.ok(saved);
    }

    /** S-03: the body of a 202 when a privileged grant became an approval request. */
    private static ResponseEntity<Map<String, String>> pending(com.dilaw.mohe.approval.model.ApprovalRequest request) {
        return ResponseEntity.accepted().body(Map.of(
                "status", "PENDING_APPROVAL",
                "message", "This is a privileged change; a second administrator must approve it.",
                "requestId", String.valueOf(request.getId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.UMS_DELETE)
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        long deletedBy = SecurityRequestUtils.requireUserId();
        userService.deleteUser(id, deletedBy);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    // ========== Password Management ==========

    @PostMapping("/{id}/change-password")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Map<String, String>> changePassword(
            @PathVariable Long id,
            @Valid @RequestBody Map<String, String> request) {
        long changedBy = SecurityRequestUtils.requireUserId();
        String newPassword = request.get("newPassword");
        userService.changePassword(id, newPassword, changedBy);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    /**
     * Lifts a lockout: the failure count, the ladder rung and the timed lock go,
     * and an account the ladder had closed returns to {@code ACTIVE}.
     *
     * <p>This is the only way out of the ladder's last rung, so it is the
     * counterpart of the policy in {@code MOHECORE.AUTH_LOCKOUT_POLICY} rather
     * than a convenience: without it a locked account needed a hand-written
     * {@code UPDATE}.
     */
    @PostMapping("/{id}/unlock")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Map<String, String>> unlockUser(@PathVariable Long id) {
        long unlockedBy = SecurityRequestUtils.requireUserId();
        userService.unlockUser(id, unlockedBy);
        return ResponseEntity.ok(Map.of("message", "User unlocked successfully"));
    }

    /**
     * O-16: clears the account's second factor (a lost or replaced phone). The
     * same administration rule as every other change to the account applies
     * (N-01, {@code UmsUserPolicy}); audited {@code MFA_RESET}. The account
     * enrols again at its next sign-in if it must, or may from its profile.
     */
    @PostMapping("/{id}/mfa/reset")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Map<String, String>> resetMfa(@PathVariable Long id) {
        mfaService.reset(id, SecurityRequestUtils.requireUserId());
        return ResponseEntity.ok(Map.of("message", "Two-factor authentication reset; the user enrols again at the next sign-in"));
    }

    @GetMapping("/{id}/mfa/status")
    @PreAuthorize(MoheSecurityExpressions.UMS_VIEW)
    public ResponseEntity<com.dilaw.mohe.ums.service.MfaService.Status> mfaStatus(@PathVariable Long id) {
        return ResponseEntity.ok(mfaService.status(id));
    }

    // ========== Group Assignment ==========

    @GetMapping("/{id}/groups")
    public ResponseEntity<List<UmsGroup>> getUserGroups(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserGroups(id));
    }

    @PostMapping("/{id}/groups/{groupId}")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Map<String, String>> assignUserToGroup(
            @PathVariable Long id,
            @PathVariable Long groupId) {
        long assignedBy = SecurityRequestUtils.requireUserId();
        return userService.assignUserToGroupOrRequest(id, groupId, assignedBy)
                .map(UmsUserController::pending)
                .orElseGet(() -> ResponseEntity.ok(Map.of("message", "User assigned to group successfully")));
    }

    @DeleteMapping("/{id}/groups/{groupId}")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Map<String, String>> removeUserFromGroup(
            @PathVariable Long id,
            @PathVariable Long groupId) {
        long removedBy = SecurityRequestUtils.requireUserId();
        userService.removeUserFromGroup(id, groupId, removedBy);
        return ResponseEntity.ok(Map.of("message", "User removed from group successfully"));
    }

    // ========== Role Assignment ==========

    @GetMapping("/{id}/roles")
    public ResponseEntity<List<UmsRole>> getUserRoles(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserRoles(id));
    }

    /** R-21: an optional window on a role assignment. */
    public record RoleWindowRequest(java.time.LocalDateTime validFrom, java.time.LocalDateTime validTo, String reason) {}

    /** R-21: copy one account's roles to another for a period. */
    public record DelegationRequest(@jakarta.validation.constraints.NotNull Long toUserId,
                                    java.time.LocalDateTime validFrom,
                                    @jakarta.validation.constraints.NotNull java.time.LocalDateTime validTo,
                                    String reason) {}

    @org.springframework.beans.factory.annotation.Value("${ums.delegation.max-days:90}")
    private int delegationMaxDays;

    @PostMapping("/{id}/roles/{roleId}")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Map<String, String>> assignRoleToUser(
            @PathVariable Long id,
            @PathVariable Long roleId,
            @RequestBody(required = false) RoleWindowRequest window) {
        long assignedBy = SecurityRequestUtils.requireUserId();
        UmsUserService.RoleWindow w = window == null ? UmsUserService.RoleWindow.NONE
                : new UmsUserService.RoleWindow(window.validFrom(), window.validTo(), null, window.reason());
        return userService.assignRoleToUserOrRequest(id, roleId, assignedBy, w)
                .map(UmsUserController::pending)
                .orElseGet(() -> ResponseEntity.ok(Map.of("message", "Role assigned to user successfully")));
    }

    /**
     * R-21: delegation. The absent account's active roles are copied to the
     * covering colleague until {@code validTo}; the nightly job takes them back.
     * Privileged roles become four-eyes requests (S-03) and are listed as pending.
     */
    @PostMapping("/{id}/delegate")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Map<String, Object>> delegateRoles(@PathVariable Long id,
                                                             @jakarta.validation.Valid @RequestBody DelegationRequest body) {
        long by = SecurityRequestUtils.requireUserId();
        UmsUserService.DelegationResult result = userService.delegateRoles(id, body.toUserId(), body.validFrom(),
                body.validTo(), body.reason(), by, delegationMaxDays);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("applied", result.applied());
        response.put("pendingRequestIds", result.pendingRequestIds());
        response.put("alreadyHeld", result.skipped());
        response.put("status", result.pendingRequestIds().isEmpty() ? "APPLIED" : "PARTLY_PENDING_APPROVAL");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Map<String, String>> removeRoleFromUser(
            @PathVariable Long id,
            @PathVariable Long roleId) {
        long removedBy = SecurityRequestUtils.requireUserId();
        userService.removeRoleFromUser(id, roleId, removedBy);
        return ResponseEntity.ok(Map.of("message", "Role removed from user successfully"));
    }

    // ========== Country Scope Assignment ==========

    @GetMapping("/{id}/countries")
    public ResponseEntity<List<UmsUserCountry>> getUserCountries(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserCountries(id));
    }

    @PostMapping("/{id}/countries/{countryId}")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Map<String, String>> assignCountryToUser(
            @PathVariable Long id,
            @PathVariable Integer countryId) {
        long assignedBy = SecurityRequestUtils.requireUserId();
        userService.assignCountryToUser(id, countryId, assignedBy);
        return ResponseEntity.ok(Map.of("message", "Country assigned to user successfully"));
    }

    @DeleteMapping("/{id}/countries/{countryId}")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Map<String, String>> removeCountryFromUser(
            @PathVariable Long id,
            @PathVariable Integer countryId) {
        long removedBy = SecurityRequestUtils.requireUserId();
        userService.removeCountryFromUser(id, countryId, removedBy);
        return ResponseEntity.ok(Map.of("message", "Country removed from user successfully"));
    }

    // ========== Direct Permission Assignment ==========

    @GetMapping("/{id}/permissions")
    public ResponseEntity<List<UmsPermission>> getUserDirectPermissions(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserDirectPermissions(id));
    }

    /**
     * R-14: a direct grant is a time-boxed exception. The body carries
     * {@code reason} and {@code expiresAt} (ISO date or date-time); both are
     * required unless the code is per-user configuration (the dashboard
     * sections), and the expiry may be at most {@code ums.direct-grant.max-days}
     * ahead.
     */
    public record DirectGrantRequest(String reason, String expiresAt, List<Integer> permissionIds) {
        java.time.LocalDateTime expiresAtOrNull() {
            if (expiresAt == null || expiresAt.isBlank()) return null;
            String v = expiresAt.trim();
            if (v.length() == 10) return java.time.LocalDate.parse(v).atTime(23, 59, 59);
            return java.time.LocalDateTime.parse(v.endsWith("Z") ? v.substring(0, v.length() - 1) : v);
        }
    }

    @PostMapping("/{id}/permissions/{permissionId}")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Map<String, String>> grantPermissionToUser(
            @PathVariable Long id,
            @PathVariable Long permissionId,
            @RequestBody(required = false) DirectGrantRequest body) {
        long grantedBy = SecurityRequestUtils.requireUserId();
        return userService.grantPermissionsToUserOrRequest(id, List.of(permissionId), grantedBy,
                        body == null ? null : body.reason(), body == null ? null : body.expiresAtOrNull())
                .map(UmsUserController::pending)
                .orElseGet(() -> ResponseEntity.ok(Map.of("message", "Permission granted to user successfully")));
    }

    @DeleteMapping("/{id}/permissions/{permissionId}")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Map<String, String>> revokePermissionFromUser(
            @PathVariable Long id,
            @PathVariable Long permissionId) {
        long revokedBy = SecurityRequestUtils.requireUserId();
        userService.revokePermissionFromUser(id, permissionId, revokedBy);
        return ResponseEntity.ok(Map.of("message", "Permission revoked from user successfully"));
    }

    @PostMapping("/{id}/permissions/batch")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Map<String, String>> grantPermissionsToUser(
            @PathVariable Long id,
            @Valid @RequestBody DirectGrantRequest request) {
        long grantedBy = SecurityRequestUtils.requireUserId();
        List<Long> longIds = (request.permissionIds() == null ? List.<Integer>of() : request.permissionIds())
                .stream().map(Integer::longValue).toList();
        return userService.grantPermissionsToUserOrRequest(id, longIds, grantedBy, request.reason(), request.expiresAtOrNull())
                .map(UmsUserController::pending)
                .orElseGet(() -> ResponseEntity.ok(Map.of("message", "Permissions granted to user successfully")));
    }

    /** R-13: the direct-grant rows with reason and expiry (the plain list above shows codes only). */
    @GetMapping("/{id}/permissions/grants")
    public ResponseEntity<List<Map<String, Object>>> getUserDirectGrants(@PathVariable Long id) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (com.dilaw.mohe.ums.model.UmsUserPermission up : userService.getUserDirectGrants(id)) {
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("permissionId", up.getPermission().getId());
            row.put("permissionCode", up.getPermission().getPermissionCode());
            row.put("reason", up.getReason());
            row.put("expiresAt", up.getExpiresAt());
            row.put("grantedAt", up.getGrantedAt());
            row.put("grantedBy", up.getGrantedBy());
            row.put("effective", up.isEffective());
            rows.add(row);
        }
        return ResponseEntity.ok(rows);
    }

    /**
     * N-03.b: active staff holding a position, optionally in one country, among
     * the accounts the caller can see; the approval-configuration screen picks
     * named approvers from this list.
     *
     * <p>Also open to whoever may edit approval rules (live QA M6): the level dialog
     * lists candidates, and Finance and HR Managers edit rules without staff access.
     * The rows are {@link UmsUserOptionDto}: name, position and countries.
     */
    @GetMapping("/approver-candidates")
    @PreAuthorize("(" + MoheSecurityExpressions.UMS_VIEW + ") or " + MoheSecurityExpressions.APPROVAL_CONFIG_WRITE)
    public ResponseEntity<List<UmsUserOptionDto>> approverCandidates(
            @RequestParam(required = false) String positionCode,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(userService.approverCandidates(blankToNull(positionCode), countryId).stream()
                .map(UmsUserOptionDto::of).toList());
    }

    @DeleteMapping("/{id}/permissions/batch")
    @PreAuthorize(MoheSecurityExpressions.UMS_EDIT)
    public ResponseEntity<Map<String, String>> revokePermissionsFromUser(
            @PathVariable Long id,
            @Valid @RequestBody Map<String, Object> request) {
        long revokedBy = SecurityRequestUtils.requireUserId();
        @SuppressWarnings("unchecked")
        List<Integer> permissionIds = (List<Integer>) request.get("permissionIds");
        List<Long> longIds = permissionIds.stream().map(Integer::longValue).toList();
        userService.revokePermissionsFromUser(id, longIds, revokedBy);
        return ResponseEntity.ok(Map.of("message", "Permissions revoked from user successfully"));
    }
}
