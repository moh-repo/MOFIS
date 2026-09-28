package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.studentportal.model.StudentChangeRequest;
import com.dilaw.mohe.util.SortWhitelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

/**
 * Admin-facing API for reviewing student change requests.
 * Listing requires STUDENT_PII (same as student data endpoints); deciding a
 * request — approve, reject, modify-and-approve — writes the change into the
 * student record and so carries {@code STUDENT_WRITE} (plan item 1.4).
 */
@RestController
@RequestMapping("api/v1/studentdata/change-requests")
@PreAuthorize(MoheSecurityExpressions.STUDENT_PII)
public class StudentChangeRequestReviewController {

    @Autowired
    private StudentChangeRequestService changeRequestService;

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            try { return Long.parseLong(auth.getName()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /** The pattern the shared {@link SortWhitelist} generalises (plan item 2.4). */
    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("createdAt", "studentId", "requestId", "status", "submissionId", "fieldName");

    /**
     * GET /api/v1/studentdata/change-requests
     * Supports: status, studentId, studentName (filters) + sortField, sortDir, page, size.
     */
    @GetMapping
    public Page<StudentChangeRequestDto> listAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer studentId,
            @RequestParam(required = false) String studentName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = SortWhitelist.sort(sortField, sortDir, ALLOWED_SORT_FIELDS);
        Pageable pageable = PageRequest.of(page, size, sort);
        return changeRequestService.getPendingRequests(status, studentId, studentName, pageable);
    }

    /**
     * POST /api/v1/studentdata/change-requests/{id}/approve
     * Body (optional): { "employeeComment": "..." }
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> approve(
            @PathVariable Integer id,
            @RequestBody(required = false) Map<String, String> body) {
        Long reviewerId = currentUserId();
        String comment = body != null ? body.get("employeeComment") : null;
        try {
            StudentChangeRequest updated = changeRequestService.approve(id, reviewerId, comment);
            // The same masking as the list (QA N3): the decision does not hand back
            // values the reviewer may not see on the student record.
            return ResponseEntity.ok(changeRequestService.toReviewDto(updated));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getReason() != null ? ex.getReason() : "Failed"));
        }
    }

    /**
     * POST /api/v1/studentdata/change-requests/{id}/reject
     * Body: { "rejectionReason": "..." }
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> reject(
            @PathVariable Integer id,
            @RequestBody Map<String, String> body) {
        Long reviewerId = currentUserId();
        String reason = body != null ? body.get("rejectionReason") : null;
        try {
            StudentChangeRequest updated = changeRequestService.reject(id, reviewerId, reason);
            // The same masking as the list (QA N3): the decision does not hand back
            // values the reviewer may not see on the student record.
            return ResponseEntity.ok(changeRequestService.toReviewDto(updated));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getReason() != null ? ex.getReason() : "Failed"));
        }
    }

    /**
     * POST /api/v1/studentdata/change-requests/{id}/modify-approve
     * Body: { "appliedValue": "...", "employeeComment": "..." }
     * Employee sets a different value before approving.
     */
    @PostMapping("/{id}/modify-approve")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> modifyAndApprove(
            @PathVariable Integer id,
            @RequestBody Map<String, String> body) {
        Long reviewerId = currentUserId();
        String modifiedValue = body != null ? body.get("appliedValue") : null;
        String comment       = body != null ? body.get("employeeComment") : null;
        if (modifiedValue == null || modifiedValue.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "'appliedValue' is required"));
        }
        try {
            StudentChangeRequest updated = changeRequestService.modifyAndApprove(id, reviewerId, modifiedValue, comment);
            // The same masking as the list (QA N3): the decision does not hand back
            // values the reviewer may not see on the student record.
            return ResponseEntity.ok(changeRequestService.toReviewDto(updated));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getReason() != null ? ex.getReason() : "Failed"));
        }
    }
}
