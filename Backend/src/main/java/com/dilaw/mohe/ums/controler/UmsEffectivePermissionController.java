package com.dilaw.mohe.ums.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.exception.ResourceNotFoundException;
import com.dilaw.mohe.ums.dto.EffectivePermissionReport;
import com.dilaw.mohe.ums.model.UmsPermissionConflict;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.dilaw.mohe.ums.service.EffectivePermissionService;
import com.dilaw.mohe.ums.service.SeparationOfDutiesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The effective-permission report and its companions (security plan R-13,
 * R-14, R-15). Read-only. Scoped like the users screen: a caller sees the
 * accounts they may administer, and always their own.
 */
@RestController
@RequestMapping("api/v1/ums/effective-permissions")
@PreAuthorize(MoheSecurityExpressions.UMS_VIEW)
public class UmsEffectivePermissionController {

    @Autowired private EffectivePermissionService reports;
    @Autowired private SeparationOfDutiesService separationOfDuties;
    @Autowired private UmsUserRepository userRepository;

    /** One account: tier, countries, roles with provenance, every code and where it comes from. */
    @GetMapping("/users/{id}")
    public ResponseEntity<EffectivePermissionReport> forUser(@PathVariable Long id) {
        return ResponseEntity.ok(reports.reportFor(id));
    }

    /** The caller's own report, for anyone signed in. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EffectivePermissionReport> forSelf() {
        long id = SecurityRequestUtils.requireUserId();
        return ResponseEntity.ok(userRepository.findById(id)
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .map(reports::reportForSelf)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id)));
    }

    /** What one account can do that another cannot, both ways. */
    @GetMapping("/users/{id}/diff/{otherId}")
    public ResponseEntity<EffectivePermissionReport.Diff> diff(@PathVariable Long id, @PathVariable Long otherId) {
        return ResponseEntity.ok(reports.diff(id, otherId));
    }

    /** R-14: live direct grants, with reason and expiry, across the accounts the caller can see. */
    @GetMapping("/exceptions")
    public ResponseEntity<List<EffectivePermissionReport.ExceptionRow>> exceptions() {
        return ResponseEntity.ok(reports.exceptions());
    }

    /** R-15: the configured pairs and the accounts that breach one. */
    @GetMapping("/separation-of-duties")
    public ResponseEntity<Map<String, Object>> separationOfDuties() {
        List<UmsPermissionConflict> pairs = separationOfDuties.activeConflicts();
        return ResponseEntity.ok(Map.of("pairs", pairs, "violations", reports.violations()));
    }
}
