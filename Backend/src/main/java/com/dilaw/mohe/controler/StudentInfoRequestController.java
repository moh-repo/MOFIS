package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.model.StudentInfoRequest;
import com.dilaw.mohe.service.StudentInfoRequestService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("api/v1/student-info-requests")
@PreAuthorize(MoheSecurityExpressions.STUDENT_INFO_REQUESTS)
public class StudentInfoRequestController {

    @Autowired
    private StudentInfoRequestService service;

    private static Long getCurrentUserId(HttpServletRequest request) {
        return SecurityRequestUtils.currentUserIdOrDefault(1L);
    }

    @PostMapping("")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_INFO_REQUESTS_WRITE)
    public ResponseEntity<?> create(@Valid @RequestBody Map<String, Object> body, HttpServletRequest request) {
        String requestType = (String) body.get("requestType");
        String title = (String) body.get("title");
        String description = (String) body.get("description");
        String dueDateStr = (String) body.get("dueDate");
        Instant dueDate = null;
        if (dueDateStr != null && !dueDateStr.isEmpty()) {
            try { dueDate = Instant.parse(dueDateStr); } catch (Exception ignored) { }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> target = (Map<String, Object>) body.get("target");
        if (target == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "target is required"));
        }
        List<Integer> studentIds = null;
        Boolean all = (Boolean) target.get("all");
        if (target.get("studentIds") instanceof List) {
            studentIds = (List<Integer>) target.get("studentIds");
        }
        Integer countryId = target.get("countryId") != null ? ((Number) target.get("countryId")).intValue() : null;
        Integer statusId = target.get("statusId") != null ? ((Number) target.get("statusId")).intValue() : null;

        List<Integer> ids = service.resolveTargetStudentIds(studentIds, Boolean.TRUE.equals(all), countryId, statusId);
        if (ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No students matched the target"));
        }
        String createdBy = "user-" + getCurrentUserId(request);
        List<StudentInfoRequest> created = service.createRequests(requestType, title, description, dueDate, createdBy, ids);
        return ResponseEntity.ok(Map.of("count", created.size(), "message", "Created " + created.size() + " request(s)"));
    }

    @GetMapping("")
    public Page<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requestType,
            @RequestParam(required = false) Integer studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
            HttpServletRequest request) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return service.findWithFiltersEnriched(createdBy, status, requestType, studentId, dateFrom, dateTo, pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable Integer id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Staging data submitted by student for review (admin view before approve/reject). */
    @GetMapping("/{id}/staging")
    public ResponseEntity<?> getStaging(@PathVariable Integer id) {
        return service.getStaging(id)
                .map(s -> ResponseEntity.ok(Map.of(
                        "studentDataJson", s.getStudentDataJson() != null ? s.getStudentDataJson() : "",
                        "studentFinanceJson", s.getStudentFinanceJson() != null ? s.getStudentFinanceJson() : "",
                        "submittedAt", s.getSubmittedAt().toString())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-student/{studentId}")
    public Page<StudentInfoRequest> byStudent(
            @PathVariable Integer studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return service.findByStudentId(studentId, pageable);
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_INFO_REQUESTS_WRITE)
    public ResponseEntity<?> cancel(@PathVariable Integer id, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        Optional<StudentInfoRequest> updated = service.cancel(id, userId);
        return updated.map(r -> ResponseEntity.ok(r)).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_INFO_REQUESTS_WRITE)
    public ResponseEntity<?> reject(@PathVariable Integer id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest request) {
        String reason = body != null ? body.get("rejectionReason") : null;
        Long userId = getCurrentUserId(request);
        Optional<StudentInfoRequest> updated = service.reject(id, reason, userId);
        return updated.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_INFO_REQUESTS_WRITE)
    public ResponseEntity<?> approve(@PathVariable Integer id, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        Optional<StudentInfoRequest> updated = service.approve(id, userId);
        return updated.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Writes the student's answer into the record. It changes student data, so it
     * needs the student write right as well as the information-request one (live
     * QA H3); a finance part also needs FINANCE.EDIT, checked in the service.
     */
    @PostMapping("/{id}/approve-and-apply")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_INFO_REQUESTS_WRITE + " and (" + MoheSecurityExpressions.STUDENT_WRITE + ")")
    public ResponseEntity<?> approveAndApply(@PathVariable Integer id, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        try {
            return service.approveAndApply(id, body != null ? body : Map.of(), getCurrentUserId(request))
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
