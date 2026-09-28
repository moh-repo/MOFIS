package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.studentportal.model.StudentPermission;
import com.dilaw.mohe.studentportal.repository.StudentPermissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Portal permission definitions. Read-only, and <b>deliberately not
 * country-scoped</b> — see {@link StudentRoleAdminController}.
 */
@RestController
@RequestMapping("api/v1/student-admin/permissions")
@PreAuthorize(MoheSecurityExpressions.STUDENT_PII)
public class StudentPermissionAdminController {

    @Autowired private StudentPermissionRepository permissionRepository;

    @GetMapping
    public List<StudentPermission> list() {
        return permissionRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentPermission> get(@PathVariable Integer id) {
        return permissionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
