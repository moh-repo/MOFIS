package com.dilaw.mohe.controler;

import com.dilaw.mohe.approval.model.ApprovalStatus;

import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.model.StudyUniversityFee;
import com.dilaw.mohe.service.StudyUniversityFeeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.dilaw.mohe.util.SortWhitelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * University fee rows. Reads under {@code FINANCE}; writes carry
 * {@code FINANCE_WRITE} (plan item 1.5). The writes only open an approval
 * request, but a VIEW-only role should not be able to open one either — the
 * approver sees a request from a finance officer, not from a reader.
 */
@RestController
@RequestMapping("api/v1/studyUniversityFee")
@PreAuthorize(MoheSecurityExpressions.FINANCE)
public class StudyUniversityFeeController {

    @Autowired
    private StudyUniversityFeeService service;

    @Autowired
    private ApprovalIntegrationService approvalIntegrationService;

    @Autowired
    private com.dilaw.mohe.config.AppTimeZone appTimeZone;

    /** Properties the fee list may be ordered by. */
    static final Set<String> SORTABLE = Set.of(
            "id", "studentId", "studyUniversityFeeStudentName", "studyUniversityFeeYear",
            "studyUniversityFeeAmount", "studyUniversityFeeDiscount", "studyUniversityRealFeeAmount",
            "studyUniversityFeePayDate", "studyUniversityFeePayStatus", "studyUniversityFeeSourceId",
            "studyUniversityCheckPayorTypeId", "studyUniversityFeeSemesterNumber", "studyUniversityFeeMidate");

    private static Long getCurrentUserId(HttpServletRequest request) {
        return SecurityRequestUtils.currentUserIdOrDefault(1L);
    }

    @GetMapping("/")
    public List<StudyUniversityFee> getAll() {
        return service.getAll();
    }

    /**
     * The fee list as the screen shows it: searched, filtered and paged on the server
     * (review F7). Dates are days in the application's time zone.
     */
    @GetMapping("/search")
    public Page<StudyUniversityFee> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
                SortWhitelist.sort(sortBy, sortDir, SORTABLE));
        return service.search(searchTerm, status,
                startDate != null ? appTimeZone.startOfDay(startDate) : null,
                endDate != null ? appTimeZone.endOfDay(endDate) : null,
                pageable);
    }

    @GetMapping("/student/{studentId}")
    public List<StudyUniversityFee> byStudent(@PathVariable Integer studentId) {
        return service.getByStudentId(studentId);
    }

    @GetMapping("/{id}")
    public StudyUniversityFee getById(@PathVariable Integer id) {
        return service.getById(id);
    }

    @PostMapping("/")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> create(@Valid @RequestBody StudyUniversityFee entity, HttpServletRequest request) {
        try {
            service.validateForRequest(entity); // R5-28: refused now, not at the approver's approve
            approvalIntegrationService.createStudyUniversityFeeRequest(
                    RequestType.INSERT, entity, null, getCurrentUserId(request));
            return ResponseEntity.ok(Map.of("message", "Request submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a student the caller may not reach keeps its 403 / 404 (N14)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> update(@PathVariable Integer id, @Valid @RequestBody StudyUniversityFee entity, HttpServletRequest request) {
        try {
            StudyUniversityFee original = service.getById(id);
            service.validateForRequest(entity); // R5-28
            approvalIntegrationService.createStudyUniversityFeeRequest(
                    RequestType.UPDATE, entity, original, getCurrentUserId(request));
            return ResponseEntity.ok(Map.of("message", "Update request submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a student the caller may not reach keeps its 403 / 404 (N14)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> delete(@PathVariable Integer id, HttpServletRequest request) {
        try {
            StudyUniversityFee original = service.getById(id);
            approvalIntegrationService.createStudyUniversityFeeRequest(
                    RequestType.DELETE, original, original, getCurrentUserId(request));
            return ResponseEntity.ok(Map.of("message", "Delete request submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a student the caller may not reach keeps its 403 / 404 (N14)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}