package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.studentportal.repository.StudentSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Admin API for managing STUDENTCORE active sessions.
 * Routes: /api/v1/student-admin/sessions/**
 *
 * <p>Listing sits under the class-level {@code STUDENT_PII}; revoking carries
 * {@code STUDENT_WRITE}, because signing a student out is a write against their
 * account whatever the verb says (plan item 1.4).
 */
@RestController
@RequestMapping("api/v1/student-admin/sessions")
@PreAuthorize(MoheSecurityExpressions.STUDENT_PII)
public class StudentSessionAdminController {

    @Autowired
    private StudentSessionRepository sessionRepository;

    @Autowired
    private StudentPortalAdminService adminService;

    /**
     * GET /api/v1/student-admin/sessions
     * ?page=0&size=20&studentUserId=&activeOnly=false
     * Default shows full history (all sessions). Pass activeOnly=true for live sessions only.
     */
    @GetMapping
    public Page<StudentSessionDto> list(
            @RequestParam(defaultValue = "0")     int page,
            @RequestParam(defaultValue = "20")    int size,
            @RequestParam(required = false)       Integer studentUserId,
            @RequestParam(defaultValue = "false") boolean activeOnly) {

        return adminService.sessions(studentUserId, activeOnly, PageRequest.of(page, size));
    }

    /** DELETE /api/v1/student-admin/sessions/{id} — revoke a single session by ID */
    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    @Transactional
    public ResponseEntity<?> revoke(@PathVariable Integer id) {
        adminService.requireSession(id);
        sessionRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Session revoked"));
    }

    /** DELETE /api/v1/student-admin/sessions/user/{studentUserId} — revoke ALL sessions for a user */
    @DeleteMapping("/user/{studentUserId}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    @Transactional
    public ResponseEntity<?> revokeAllForUser(@PathVariable Integer studentUserId) {
        adminService.requireUser(studentUserId);
        sessionRepository.deleteByStudentUserId(studentUserId);
        return ResponseEntity.ok(Map.of("message", "All sessions revoked for user " + studentUserId));
    }

    /**
     * DELETE /api/v1/student-admin/sessions/expired — purge all expired sessions.
     *
     * <p>Ministry-only rather than scoped. It is housekeeping across the whole
     * table, and there is no useful country-scoped version of it: a partial purge
     * leaves the table in a state nobody asked for and makes the next run's
     * result depend on who happened to press the button.
     */
    @DeleteMapping("/expired")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    @Transactional
    public ResponseEntity<?> purgeExpired() {
        adminService.requireMinistry("Purging expired sessions");
        sessionRepository.deleteExpired(Instant.now());
        return ResponseEntity.ok(Map.of("message", "Expired sessions purged"));
    }
}
