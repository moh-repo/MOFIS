package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.studentportal.model.StudentChangeRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Student-facing API for submitting and viewing own change requests.
 * Protected by StudentPortalTokenInterceptor (X-Student-Portal-Token required).
 *
 * <p>Every message a student can see comes from the {@code portal.*} bundle keys,
 * resolved in the request's locale. They were English literals on an Arabic
 * portal.
 */
@RestController
@RequestMapping("api/v1/student-portal/change-requests")
public class StudentChangeRequestController {

    @Autowired
    private StudentChangeRequestService changeRequestService;

    @Autowired
    private MessageSource messageSource;

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    private static int getStudentId(HttpServletRequest request) {
        Object attr = request.getAttribute(StudentPortalTokenInterceptor.ATTR_STUDENT_ID);
        if (attr instanceof Integer id) return id;
        throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Not authenticated");
    }

    private static Integer getStudentUserId(HttpServletRequest request) {
        Object attr = request.getAttribute(StudentPortalTokenInterceptor.ATTR_STUDENT_USER_ID);
        return attr instanceof Integer id ? id : null;
    }

    /** GET /api/v1/student-portal/change-requests — list own requests */
    @GetMapping
    public ResponseEntity<List<StudentChangeRequest>> getMyRequests(HttpServletRequest request) {
        int studentId = getStudentId(request);
        return ResponseEntity.ok(changeRequestService.getMyRequests(studentId));
    }

    /**
     * POST /api/v1/student-portal/change-requests — submit one or more field changes.
     * Body: { "changes": [{ "fieldName": "studentName", "fieldLabel": "Name", "newValue": "Ahmed" }] }
     */
    @PostMapping
    public ResponseEntity<?> submit(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        int studentId = getStudentId(request);
        Integer studentUserId = getStudentUserId(request);
        if (studentUserId == null) {
            return ResponseEntity.status(401).body(Map.of("error", msg("portal.changeRequest.legacySession")));
        }

        Object changesRaw = body.get("changes");
        if (!(changesRaw instanceof List<?> rawList)) {
            return ResponseEntity.badRequest().body(Map.of("error", msg("portal.changeRequest.changesMissing")));
        }

        List<StudentChangeRequestService.FieldChangeInput> changes = rawList.stream()
                .filter(item -> item instanceof Map)
                .map(item -> {
                    @SuppressWarnings("unchecked")
                    Map<String, String> m = (Map<String, String>) item;
                    return new StudentChangeRequestService.FieldChangeInput(
                            m.get("fieldName"),
                            m.get("fieldLabel"),
                            m.get("newValue")
                    );
                }).toList();

        try {
            List<StudentChangeRequest> created = changeRequestService.submitRequest(studentId, studentUserId, changes);
            return ResponseEntity.ok(Map.of(
                    "message", msg("portal.changeRequest.submitted"),
                    "submissionId", created.get(0).getSubmissionId(),
                    "count", created.size()
            ));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getReason() != null ? ex.getReason() : msg("portal.changeRequest.submitFailed")));
        }
    }

    /** DELETE /api/v1/student-portal/change-requests/{id} — cancel a PENDING request */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancel(@PathVariable Integer id, HttpServletRequest request) {
        Integer studentUserId = getStudentUserId(request);
        if (studentUserId == null) {
            return ResponseEntity.status(401).body(Map.of("error", msg("portal.changeRequest.legacySessionCancel")));
        }
        try {
            changeRequestService.cancelRequest(id, studentUserId);
            return ResponseEntity.ok(Map.of("message", msg("portal.changeRequest.cancelled")));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getReason() != null ? ex.getReason() : msg("portal.changeRequest.cancelFailed")));
        }
    }

    /**
     * GET /api/v1/student-portal/change-requests/allowed-fields — the fields the student
     * can request to change, and each one's DATA_TYPE (TEXT, DATE, LOOKUP, EMAIL, PHONE)
     * so the form draws a date picker for a date (review R2b).
     */
    @GetMapping("/allowed-fields")
    public ResponseEntity<?> getAllowedFields() {
        return ResponseEntity.ok(Map.of(
                "fields", StudentChangeRequestService.ALLOWED_FIELDS,
                "types", changeRequestService.fieldTypes()));
    }
}
