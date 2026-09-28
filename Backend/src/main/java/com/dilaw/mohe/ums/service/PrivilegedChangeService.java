package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.ApprovalStatus;
import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.repository.ApprovalRequestRepository;
import com.dilaw.mohe.approval.service.ApprovalWorkflowService;
import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.model.UmsPermission;
import com.dilaw.mohe.ums.model.UmsRole;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.repository.UmsPermissionRepository;
import com.dilaw.mohe.ums.repository.UmsRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Four-eyes on privileged administration (security plan S-03).
 *
 * <p>The system has had maker-checker for money since the ledger was written;
 * the rights that <em>control</em> the money had none: one administrator could
 * hand out {@code PAYMENT_BATCH.APPROVE}, promote an account to admin, or
 * restore a backup alone. Now a privileged change is <em>requested</em> and a
 * second administrator approves it through the ordinary workflow (entity types
 * {@code USER_PRIVILEGE_CHANGE} and {@code SYSTEM_RESTORE}, configured by
 * V096 with one level for the {@code MINISTRY_ADMIN} role, routed by N-03 like
 * any other level). The super admin is exempt and every direct privileged change
 * it makes is audited ({@code PRIVILEGE_CHANGE_DIRECT}).
 *
 * <p>Privileged means: any tier role; any role, group or code that carries a
 * {@code *.APPROVE} code, {@code SYSTEM.RESTORE} or {@code UMS_ADMIN.*}; the
 * administrator flag. Everything else is applied at once, as before.
 *
 * <p>The requester is checked for the right to make the change <em>when
 * requesting</em> (the privilege guard and the separation-of-duties pairs), so a
 * request that would be refused is refused immediately rather than after a
 * colleague has signed it; the approver is checked again on execution, because
 * the execution runs as the approver.
 */
@Service
public class PrivilegedChangeService {

    private static final Logger log = LoggerFactory.getLogger(PrivilegedChangeService.class);

    public static final String ENTITY_USER_PRIVILEGE = "USER_PRIVILEGE_CHANGE";
    public static final String ENTITY_SYSTEM_RESTORE = "SYSTEM_RESTORE";

    /** The one change a request carries. Serialised into {@code RequestData}. */
    public record Change(String kind, Long userId, Long roleId, Long groupId, List<Long> permissionIds,
                         String reason, String expiresAt, Boolean isAdmin,
                         String validFrom, Long delegatedFrom) {

        public static Change role(Long userId, Long roleId) {
            return new Change("ROLE", userId, roleId, null, null, null, null, null, null, null);
        }

        /** R-21: a role with a window (expiresAt doubles as validTo) or a delegation. */
        public static Change role(Long userId, Long roleId, UmsUserService.RoleWindow window) {
            if (window == null || window.isEmpty()) return role(userId, roleId);
            return new Change("ROLE", userId, roleId, null, null, window.reason(),
                    window.validTo() == null ? null : window.validTo().toString(), null,
                    window.validFrom() == null ? null : window.validFrom().toString(), window.delegatedFrom());
        }

        public static Change group(Long userId, Long groupId) {
            return new Change("GROUP", userId, null, groupId, null, null, null, null, null, null);
        }

        public static Change permissions(Long userId, List<Long> permissionIds, String reason, LocalDateTime expiresAt) {
            return new Change("PERMISSIONS", userId, null, null, permissionIds, reason,
                    expiresAt == null ? null : expiresAt.toString(), null, null, null);
        }

        public static Change adminFlag(Long userId) {
            return new Change("ADMIN_FLAG", userId, null, null, null, null, null, true, null, null);
        }

        public UmsUserService.RoleWindow roleWindow() {
            return new UmsUserService.RoleWindow(
                    validFrom == null ? null : LocalDateTime.parse(validFrom),
                    expiresAt == null ? null : LocalDateTime.parse(expiresAt),
                    delegatedFrom, reason);
        }

        public String describe() {
            return switch (kind) {
                case "ROLE" -> "role " + roleId + (expiresAt != null ? " until " + expiresAt : "")
                        + (delegatedFrom != null ? " (delegated from user " + delegatedFrom + ")" : "");
                case "GROUP" -> "group " + groupId;
                case "PERMISSIONS" -> "permissions " + permissionIds;
                case "ADMIN_FLAG" -> "administrator flag";
                default -> kind;
            };
        }
    }

    private final AdminTierService adminTierService;
    private final SeparationOfDutiesService separationOfDuties;
    private final UmsRoleRepository roleRepository;
    private final UmsPermissionRepository permissionRepository;
    private final ApprovalRequestRepository requestRepository;
    private final UmsAuditLogService auditLogService;
    private final ApprovalWorkflowService workflow;
    private final UmsUserService userService;
    private final boolean enabled;
    private final ObjectMapper json = new ObjectMapper();

    public PrivilegedChangeService(AdminTierService adminTierService,
                                   SeparationOfDutiesService separationOfDuties,
                                   UmsRoleRepository roleRepository,
                                   UmsPermissionRepository permissionRepository,
                                   ApprovalRequestRepository requestRepository,
                                   UmsAuditLogService auditLogService,
                                   @Lazy ApprovalWorkflowService workflow,
                                   @Lazy UmsUserService userService,
                                   @Value("${ums.four-eyes.enabled:true}") boolean enabled) {
        this.adminTierService = adminTierService;
        this.separationOfDuties = separationOfDuties;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.requestRepository = requestRepository;
        this.auditLogService = auditLogService;
        this.workflow = workflow;
        this.userService = userService;
        this.enabled = enabled;
    }

    // ── What is privileged ───────────────────────────────────────────────────

    public static boolean isPrivilegedCode(String code) {
        if (code == null) return false;
        return code.endsWith(".APPROVE") || "SYSTEM.RESTORE".equals(code) || code.startsWith("UMS_ADMIN.");
    }

    public boolean roleIsPrivileged(Long roleId) {
        Optional<UmsRole> role = roleRepository.findById(roleId);
        if (role.isEmpty()) return false;
        if (AdminTier.fromRoleName(role.get().getRoleName()).isPresent()) return true;
        return separationOfDuties.codesOfRole(roleId).stream().anyMatch(PrivilegedChangeService::isPrivilegedCode);
    }

    public boolean groupIsPrivileged(Long groupId) {
        return separationOfDuties.codesOfGroup(groupId).stream().anyMatch(PrivilegedChangeService::isPrivilegedCode);
    }

    public boolean permissionIsPrivileged(Long permissionId) {
        return permissionRepository.findById(permissionId).map(UmsPermission::getPermissionCode)
                .map(PrivilegedChangeService::isPrivilegedCode).orElse(false);
    }

    /** Whether this caller's privileged changes go through the workflow: on, and not the super admin. */
    public boolean requiresApproval(Long callerId) {
        return enabled && callerId != null && adminTierService.tierOf(callerId) != AdminTier.SUPER_ADMIN;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ── Request and execute ──────────────────────────────────────────────────

    /** Raises the request; the caller has already verified the requester may make the change. */
    @Transactional
    public ApprovalRequest request(Change change, Long requestedBy) {
        String entityId = "user:" + change.userId() + ":" + change.kind() + ":"
                + (change.roleId() != null ? change.roleId()
                : change.groupId() != null ? change.groupId()
                : change.permissionIds() != null ? String.valueOf(change.permissionIds().hashCode()) : "flag");
        ApprovalRequest request = workflow.createRequest(ENTITY_USER_PRIVILEGE, RequestType.UPDATE, entityId,
                toJson(change), null, requestedBy);
        auditLogService.log(requestedBy, "PRIVILEGE_CHANGE_REQUESTED", "USER", change.userId(), null,
                change.describe() + " (approval request " + request.getId() + ")");
        return request;
    }

    /** The super admin's exemption leaves a row: what was granted directly and to whom. */
    public void auditDirect(Long by, Change change) {
        auditLogService.log(by, "PRIVILEGE_CHANGE_DIRECT", "USER", change.userId(), null,
                change.describe() + " (super admin, four-eyes exempt)");
    }

    /** Applies an approved change as the approver who executed it. */
    @Transactional
    public void execute(ApprovalRequest request) {
        Change change = fromJson(request.getRequestData());
        Long by = request.getExecutedBy() != null ? request.getExecutedBy() : request.getRequestedBy();
        switch (change.kind()) {
            case "ROLE" -> userService.assignRoleToUser(change.userId(), change.roleId(), by, change.roleWindow());
            case "GROUP" -> userService.assignUserToGroup(change.userId(), change.groupId(), by);
            case "PERMISSIONS" -> userService.grantPermissionsToUser(change.userId(), change.permissionIds(), by,
                    change.reason(), change.expiresAt() == null ? null : LocalDateTime.parse(change.expiresAt()));
            case "ADMIN_FLAG" -> {
                UmsUser patch = new UmsUser();
                patch.setIsAdmin(true);
                userService.updateUser(change.userId(), patch, by);
            }
            default -> throw new IllegalStateException("Unknown privileged change kind " + change.kind());
        }
        auditLogService.log(by, "PRIVILEGE_CHANGE_EXECUTED", "USER", change.userId(), null,
                change.describe() + " (approval request " + request.getId() + ", requested by " + request.getRequestedBy() + ")");
    }

    // ── Restore ──────────────────────────────────────────────────────────────

    /** Raises the restore request for one backup file; a second administrator approves it. */
    @Transactional
    public ApprovalRequest requestRestore(String fileName, long size, Long requestedBy) {
        String data = "{\"fileName\":" + quote(fileName) + ",\"size\":" + size + "}";
        ApprovalRequest request = workflow.createRequest(ENTITY_SYSTEM_RESTORE, RequestType.UPDATE, fileName,
                data, null, requestedBy);
        auditLogService.log(requestedBy, "SYSTEM_RESTORE_REQUESTED", "BACKUP", null, null,
                fileName + " (approval request " + request.getId() + ")");
        return request;
    }

    /**
     * Whether an approved restore ticket exists for this file and this caller:
     * executed within the last 24 hours, raised by the same person. The ticket
     * is consumed by the audit row the restore itself writes; a second restore
     * needs a second ticket only if a day has passed.
     */
    public boolean restoreApproved(String fileName, Long callerId) {
        if (fileName == null || callerId == null) return false;
        java.time.Instant since = java.time.Instant.now().minus(java.time.Duration.ofHours(24));
        return requestRepository.findByEntityTypeAndEntityIdAndStatusIn(ENTITY_SYSTEM_RESTORE, fileName,
                        List.of(ApprovalStatus.EXECUTED))
                .stream().anyMatch(r -> callerId.equals(r.getRequestedBy())
                        && r.getExecutedAt() != null && r.getExecutedAt().isAfter(since));
    }

    // ── JSON ─────────────────────────────────────────────────────────────────

    private String toJson(Change change) {
        try {
            return json.writeValueAsString(change);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise the privileged change", e);
        }
    }

    public Change fromJson(String data) {
        try {
            return json.readValue(data, Change.class);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the privileged change: " + e.getMessage(), e);
        }
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
