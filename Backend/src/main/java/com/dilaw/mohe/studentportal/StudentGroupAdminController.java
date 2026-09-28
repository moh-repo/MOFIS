package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.studentportal.model.StudentGroup;
import com.dilaw.mohe.studentportal.model.StudentGroupRole;
import com.dilaw.mohe.studentportal.repository.StudentGroupRepository;
import com.dilaw.mohe.studentportal.repository.StudentGroupRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Portal group definitions.
 *
 * <p><b>Deliberately not country-scoped</b> — see
 * {@link StudentRoleAdminController} for the reasoning. Worth noting explicitly
 * here because a group <em>could</em> leak names: it does not, because this
 * controller exposes only the group and its roles. The membership list lives on
 * {@code /student-admin/users/{id}/groups}, which is gated.
 *
 * <p>Writes carry {@code STUDENT_WRITE}; the class-level {@code STUDENT_PII}
 * admits VIEW-only codes and is for the reads (plan item 1.4).
 */
@RestController
@RequestMapping("api/v1/student-admin/groups")
@PreAuthorize(MoheSecurityExpressions.STUDENT_PII)
public class StudentGroupAdminController {

    @Autowired private StudentGroupRepository groupRepository;
    @Autowired private StudentGroupRoleRepository groupRoleRepository;

    @GetMapping
    public List<StudentGroup> list() {
        return groupRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentGroup> get(@PathVariable Integer id) {
        return groupRepository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/roles")
    public List<StudentGroupRole> getRoles(@PathVariable Integer id) {
        return groupRoleRepository.findByGroupId(id);
    }

    @PostMapping
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<StudentGroup> create(@RequestBody StudentGroup group) {
        group.setGroupId(null);
        group.setIsSystem(false);
        return ResponseEntity.ok(groupRepository.save(group));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody StudentGroup body) {
        return groupRepository.findById(id).map(group -> {
            if (Boolean.TRUE.equals(group.getIsSystem())) {
                return ResponseEntity.badRequest().<Object>body(Map.of("error", "System groups cannot be modified"));
            }
            group.setGroupName(body.getGroupName());
            group.setGroupNameAr(body.getGroupNameAr());
            group.setDescription(body.getDescription());
            group.setIsActive(body.getIsActive());
            return ResponseEntity.<Object>ok(groupRepository.save(group));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        return groupRepository.findById(id).map(group -> {
            if (Boolean.TRUE.equals(group.getIsSystem())) {
                return ResponseEntity.badRequest().<Object>body(Map.of("error", "System groups cannot be deleted"));
            }
            groupRepository.deleteById(id);
            return ResponseEntity.<Object>ok(Map.of("message", "Group deleted"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/roles/{roleId}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> assignRole(@PathVariable Integer id, @PathVariable Integer roleId) {
        if (groupRoleRepository.existsByGroupIdAndRoleId(id, roleId)) {
            return ResponseEntity.ok(Map.of("message", "Already assigned"));
        }
        StudentGroupRole gr = new StudentGroupRole();
        gr.setGroupId(id);
        gr.setRoleId(roleId);
        groupRoleRepository.save(gr);
        return ResponseEntity.ok(Map.of("message", "Role assigned to group"));
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> removeRole(@PathVariable Integer id, @PathVariable Integer roleId) {
        groupRoleRepository.deleteByGroupIdAndRoleId(id, roleId);
        return ResponseEntity.ok(Map.of("message", "Role removed from group"));
    }
}
