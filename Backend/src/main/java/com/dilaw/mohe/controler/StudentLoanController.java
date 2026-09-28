package com.dilaw.mohe.controler;

import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.StudentLoan;
import com.dilaw.mohe.service.StudentLoanService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Reads under {@code FINANCE}; writes carry {@code FINANCE_WRITE} (plan item 1.5,
 * same split as A5).
 *
 * <p>The writes only open an approval request, but a VIEW-only role should not be
 * able to open one either — the approver sees a request from a finance officer,
 * not from a reader.
 */
@RestController
@RequestMapping("api/v1/studentLoan")
@PreAuthorize(MoheSecurityExpressions.FINANCE)
public class StudentLoanController {

    @Autowired
    private StudentLoanService service;

    @Autowired
    private ApprovalIntegrationService approvalIntegrationService;

    @GetMapping({"", "/"})
    public List<StudentLoan> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public StudentLoan getById(@PathVariable Integer id) {
        return service.getById(id);
    }

    @GetMapping("/student/{studentId}")
    public List<StudentLoan> byStudent(@PathVariable Integer studentId) {
        return service.getByStudentId(studentId);
    }

    @PostMapping("/")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> create(@Valid @RequestBody StudentLoan entity) {
        try {
            approvalIntegrationService.createStudentLoanRequest(
                    RequestType.INSERT, entity, null, null, null, PendingApprovalResponse.currentUserId());
            return ResponseEntity.ok(PendingApprovalResponse.CREATE);
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a student the caller may not reach keeps its 403 / 404 (N14)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * The body stays a JSON tree so the service can tell a column the dialog
     * omitted from one it deliberately cleared — see {@code StudentLoanService}.
     * The tree is what the request stores, so the same distinction survives until
     * an approver signs off.
     */
    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody JsonNode body) {
        try {
            StudentLoan original = service.getById(id);
            approvalIntegrationService.createStudentLoanRequest(
                    RequestType.UPDATE, null, body, id, original, PendingApprovalResponse.currentUserId());
            return ResponseEntity.ok(PendingApprovalResponse.UPDATE);
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a student the caller may not reach keeps its 403 / 404 (N14)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        try {
            StudentLoan original = service.getById(id);
            approvalIntegrationService.createStudentLoanRequest(
                    RequestType.DELETE, original, null, id, original, PendingApprovalResponse.currentUserId());
            return ResponseEntity.ok(PendingApprovalResponse.DELETE);
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a student the caller may not reach keeps its 403 / 404 (N14)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
