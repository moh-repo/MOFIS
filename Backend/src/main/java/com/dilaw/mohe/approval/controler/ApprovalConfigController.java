package com.dilaw.mohe.approval.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.approval.model.ApprovalConfig;
import com.dilaw.mohe.approval.model.ApprovalLevel;
import com.dilaw.mohe.approval.service.ApprovalConfigService;
import com.dilaw.mohe.ums.service.UmsRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for approval configuration management (admin only).
 */
@RestController
@RequestMapping("api/v1/approval-configs")
@PreAuthorize(MoheSecurityExpressions.APPROVALS)
public class ApprovalConfigController {

    @Autowired
    private ApprovalConfigService configService;

    @Autowired
    private UmsRoleService roleService;

    /** A role a level may require: its id and names, nothing else. */
    public record RoleOption(Long id, String roleName, String roleNameAr) {
    }

    /**
     * Get all configurations.
     */
    @GetMapping
    public ResponseEntity<List<ApprovalConfig>> getAllConfigs() {
        return ResponseEntity.ok(configService.getAllConfigs());
    }

    /**
     * Get all active configurations.
     */
    @GetMapping("/active")
    public ResponseEntity<List<ApprovalConfig>> getActiveConfigs() {
        return ResponseEntity.ok(configService.getActiveConfigs());
    }

    /**
     * The roles a level may require (live QA M6). The rules page read the staff role list,
     * which needs the staff-administration right, so every approvals role without it got
     * "Access denied" and the levels showed role ids. Names only, under this page's read
     * gate, because the levels list shows them to everyone who may open it.
     */
    @GetMapping("/role-options")
    public ResponseEntity<List<RoleOption>> getRoleOptions() {
        return ResponseEntity.ok(roleService.getAllActiveRoles().stream()
                .map(r -> new RoleOption(r.getId(), r.getRoleName(), r.getRoleNameAr()))
                .toList());
    }

    /**
     * Get a specific configuration by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApprovalConfig> getConfigById(@PathVariable Long id) {
        return configService.getConfigById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get configuration by entity type.
     */
    @GetMapping("/entity/{entityType}")
    public ResponseEntity<ApprovalConfig> getConfigByEntityType(@PathVariable String entityType) {
        return configService.getConfigByEntityType(entityType)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new configuration.
     */
    @PostMapping
    @PreAuthorize(MoheSecurityExpressions.APPROVAL_CONFIG_WRITE)
    public ResponseEntity<?> createConfig(@Valid @RequestBody ApprovalConfig config) {
        try {
            ApprovalConfig created = configService.createConfig(config);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update a configuration.
     */
    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.APPROVAL_CONFIG_WRITE)
    public ResponseEntity<?> updateConfig(@PathVariable Long id, @Valid @RequestBody ApprovalConfig config) {
        try {
            ApprovalConfig updated = configService.updateConfig(id, config);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Deactivate a configuration.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.APPROVAL_CONFIG_WRITE)
    public ResponseEntity<?> deactivateConfig(@PathVariable Long id) {
        try {
            configService.deactivateConfig(id);
            return ResponseEntity.ok(Map.of("message", "Configuration deactivated"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ========== Level Management ==========

    /**
     * Get levels for a configuration.
     */
    @GetMapping("/{configId}/levels")
    public ResponseEntity<List<ApprovalLevel>> getLevels(@PathVariable Long configId) {
        return ResponseEntity.ok(configService.getLevelsForConfig(configId));
    }

    /**
     * Add a level to a configuration.
     */
    @PostMapping("/{configId}/levels")
    @PreAuthorize(MoheSecurityExpressions.APPROVAL_CONFIG_WRITE)
    public ResponseEntity<?> addLevel(@PathVariable Long configId, @Valid @RequestBody ApprovalLevel level) {
        try {
            ApprovalLevel created = configService.addLevel(configId, level);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update a level.
     */
    @PutMapping("/levels/{levelId}")
    @PreAuthorize(MoheSecurityExpressions.APPROVAL_CONFIG_WRITE)
    public ResponseEntity<?> updateLevel(@PathVariable Long levelId, @Valid @RequestBody ApprovalLevel level) {
        try {
            ApprovalLevel updated = configService.updateLevel(levelId, level);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reorder levels within a configuration.
     */
    @PostMapping("/{configId}/levels/reorder")
    @PreAuthorize(MoheSecurityExpressions.APPROVAL_CONFIG_WRITE)
    public ResponseEntity<?> reorderLevels(@PathVariable Long configId, @Valid @RequestBody List<Long> levelIds) {
        try {
            configService.reorderLevels(configId, levelIds);
            return ResponseEntity.ok(Map.of("message", "Levels reordered successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a level.
     */
    @DeleteMapping("/levels/{levelId}")
    @PreAuthorize(MoheSecurityExpressions.APPROVAL_CONFIG_WRITE)
    public ResponseEntity<?> deleteLevel(@PathVariable Long levelId) {
        try {
            configService.deleteLevel(levelId);
            return ResponseEntity.ok(Map.of("message", "Level deleted"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
