package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.studentportal.model.StudentUser;
import com.dilaw.mohe.studentportal.model.StudentUserGroup;
import com.dilaw.mohe.studentportal.repository.StudentUserGroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin API for managing STUDENTCORE student portal users.
 * Routes: /api/v1/student-admin/users/**
 *
 * <p>Reads sit under the class-level {@code STUDENT_PII} (any student-page
 * permission, VIEW included). Every write carries {@code STUDENT_WRITE}: until
 * the 27 Aug 2026 pass (plan item 1.4) a {@code STUDENTS.VIEW}-only account
 * could reset any portal password and lock any account. A method-level
 * annotation replaces the class-level one, so the constant stands on its own.
 */
@RestController
@RequestMapping("api/v1/student-admin/users")
@PreAuthorize(MoheSecurityExpressions.STUDENT_PII)
public class StudentUserAdminController {

    private final BCryptPasswordEncoder encoder = com.dilaw.mohe.config.Passwords.encoder();

    @Autowired private StudentUserGroupRepository userGroupRepository;
    @Autowired private StudentPortalAdminService adminService;
    @Autowired private CodeListService codeLists;
    @Autowired(required = false) private org.springframework.context.MessageSource messageSource;

    /** GET /api/v1/student-admin/users?page=0&size=20&search=&status= */
    @GetMapping
    public Page<StudentUser> list(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(defaultValue = "")    String search,
            @RequestParam(required = false)     String status) {

        PageRequest pr = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return adminService.users(search, status, pr);
    }

    /** GET /api/v1/student-admin/users/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<StudentUser> get(@PathVariable Integer id) {
        return ResponseEntity.ok(adminService.requireUser(id));
    }

    /** GET /api/v1/student-admin/users/{id}/groups */
    @GetMapping("/{id}/groups")
    public List<StudentUserGroup> getGroups(@PathVariable Integer id) {
        adminService.requireUser(id);
        return userGroupRepository.findByStudentUserId(id);
    }

    /** PUT /api/v1/student-admin/users/{id}/status — body: { "status": "ACTIVE"|"INACTIVE"|"LOCKED" } */
    @PutMapping("/{id}/status")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> updateStatus(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        // The accepted values are the active rows of LU_STUDENT_PORTAL_ACCOUNT_STATUS.
        if (status == null || !codeLists.codes("portal-account-status").contains(status)) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Invalid status. Use one of " + codeLists.codes("portal-account-status")));
        }
        adminService.updateStatus(id, status);
        return ResponseEntity.ok(Map.of("message", "Status updated to " + status));
    }

    /** POST /api/v1/student-admin/users/{id}/reset-password — body: { "newPassword": "..." } */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> resetPassword(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        // The gate matters most here: STUDENT_LOGIN.RESET is held by every
        // country admin, and without it a Jordan officer could take over any of
        // the Egyptian students' portal accounts.
        StudentUser u = adminService.requireUser(id);
        com.dilaw.mohe.ums.service.PasswordPolicy.Violation problem = com.dilaw.mohe.ums.service.PasswordPolicy
                .violation(newPassword, u.getUsername(), com.dilaw.mohe.ums.service.PasswordPolicy.PORTAL_MIN); // O-25
        if (problem != null) {
            // In the officer's language (live QA L12, 24 Sep 2026).
            return ResponseEntity.badRequest().body(Map.of("error", problem.localized(messageSource,
                    org.springframework.context.i18n.LocaleContextHolder.getLocale())));
        }
        // Stores the password and ends every session of the account, in one transaction.
        adminService.resetPassword(u, encoder.encode(newPassword));
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    /** POST /api/v1/student-admin/users/{id}/groups/{groupId} — assign group */
    @PostMapping("/{id}/groups/{groupId}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> assignGroup(@PathVariable Integer id, @PathVariable Integer groupId) {
        adminService.requireUser(id);
        if (userGroupRepository.existsByStudentUserIdAndGroupId(id, groupId)) {
            return ResponseEntity.ok(Map.of("message", "Already assigned"));
        }
        StudentUserGroup ug = new StudentUserGroup();
        ug.setStudentUserId(id);
        ug.setGroupId(groupId);
        userGroupRepository.save(ug);
        return ResponseEntity.ok(Map.of("message", "Group assigned"));
    }

    /** DELETE /api/v1/student-admin/users/{id}/groups/{groupId} — remove group */
    @DeleteMapping("/{id}/groups/{groupId}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> removeGroup(@PathVariable Integer id, @PathVariable Integer groupId) {
        adminService.requireUser(id);
        userGroupRepository.deleteByStudentUserIdAndGroupId(id, groupId);
        return ResponseEntity.ok(Map.of("message", "Group removed"));
    }
}
