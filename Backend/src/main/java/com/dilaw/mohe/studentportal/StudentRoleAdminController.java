package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.studentportal.model.StudentPermission;
import com.dilaw.mohe.studentportal.model.StudentRole;
import com.dilaw.mohe.studentportal.model.StudentRolePermission;
import com.dilaw.mohe.studentportal.repository.StudentPermissionRepository;
import com.dilaw.mohe.studentportal.repository.StudentRolePermissionRepository;
import com.dilaw.mohe.studentportal.repository.StudentRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Portal role definitions.
 *
 * <p><b>Deliberately not country-scoped</b>, and that is a decision rather than
 * an omission. A role is the vocabulary the portal's permissions are written in,
 * shared by every attaché, and it exposes no member list — only the role and the
 * permissions attached to it. Scoping it would leave a country officer with an
 * empty list and nothing to assign, which breaks the screen rather than
 * protecting it. Same reasoning as the الدليل المحاسبي الموحد in
 * {@code CountryColumnSpec.countryScopeOrShared}.
 *
 * <p>What is scoped is the <em>assignment</em> of a role to an account, in
 * {@link StudentPortalAdminService} — because that names a student.
 *
 * <p>Writes carry {@code STUDENT_WRITE}; the class-level {@code STUDENT_PII}
 * admits VIEW-only codes and is for the reads (plan item 1.4).
 */
@RestController
@RequestMapping("api/v1/student-admin/roles")
@PreAuthorize(MoheSecurityExpressions.STUDENT_PII)
public class StudentRoleAdminController {

    @Autowired private StudentRoleRepository roleRepository;
    @Autowired private StudentRolePermissionRepository rolePermissionRepository;
    @Autowired private StudentPermissionRepository permissionRepository;

    @GetMapping
    public List<StudentRole> list() {
        return roleRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentRole> get(@PathVariable Integer id) {
        return roleRepository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/permissions")
    public List<StudentPermission> getPermissions(@PathVariable Integer id) {
        return permissionRepository.findByRoleId(id);
    }

    @PostMapping
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<StudentRole> create(@RequestBody StudentRole role) {
        role.setRoleId(null);
        role.setIsSystem(false);
        return ResponseEntity.ok(roleRepository.save(role));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody StudentRole body) {
        return roleRepository.findById(id).map(role -> {
            if (Boolean.TRUE.equals(role.getIsSystem())) {
                return ResponseEntity.badRequest().<Object>body(Map.of("error", "System roles cannot be modified"));
            }
            role.setRoleName(body.getRoleName());
            role.setRoleNameAr(body.getRoleNameAr());
            role.setDescription(body.getDescription());
            role.setIsActive(body.getIsActive());
            return ResponseEntity.<Object>ok(roleRepository.save(role));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        return roleRepository.findById(id).map(role -> {
            if (Boolean.TRUE.equals(role.getIsSystem())) {
                return ResponseEntity.badRequest().<Object>body(Map.of("error", "System roles cannot be deleted"));
            }
            roleRepository.deleteById(id);
            return ResponseEntity.<Object>ok(Map.of("message", "Role deleted"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/permissions/{permissionId}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> assignPermission(@PathVariable Integer id, @PathVariable Integer permissionId) {
        if (rolePermissionRepository.existsByRoleIdAndPermissionId(id, permissionId)) {
            return ResponseEntity.ok(Map.of("message", "Already assigned"));
        }
        StudentRolePermission rp = new StudentRolePermission();
        rp.setRoleId(id);
        rp.setPermissionId(permissionId);
        rolePermissionRepository.save(rp);
        return ResponseEntity.ok(Map.of("message", "Permission assigned"));
    }

    @DeleteMapping("/{id}/permissions/{permissionId}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> removePermission(@PathVariable Integer id, @PathVariable Integer permissionId) {
        rolePermissionRepository.deleteByRoleIdAndPermissionId(id, permissionId);
        return ResponseEntity.ok(Map.of("message", "Permission removed"));
    }
}
