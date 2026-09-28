package com.dilaw.mohe.controler;

import com.dilaw.mohe.approval.model.ApprovalStatus;

import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.approval.service.ApprovalWorkflowService;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.model.StudentFinanceWafar;
import com.dilaw.mohe.service.StudentFinanceWafarService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Wafar records.
 *
 * Reads come straight from the table. Writes are submitted to the approval
 * workflow and only reach STUDENT_FINANCE_WAFAR once approved, so this screen
 * cannot bypass the gate the finance form now goes through.
 */
@RestController
@RequestMapping("api/v1/studentFinanceWafar")
@PreAuthorize(MoheSecurityExpressions.WAFAR)
public class StudentFinanceWafarController {

    @Autowired
    private StudentFinanceWafarService service;

    @Autowired
    private ApprovalIntegrationService approvalIntegrationService;

    @Autowired
    private ApprovalWorkflowService workflowService;

    /**
     * Student ids with a wafar change awaiting a decision (review F9): wafar requests
     * raised here, and finance requests, which carry the wafar the finance form edits.
     * A wafar row is written only on approval, so without this the list looked as if
     * a save had done nothing. The approvals endpoint that has the same answer is
     * behind APPROVALS, which the wafar roles do not hold. Scoped like that endpoint.
     */
    @GetMapping("/pending")
    public Map<String, List<String>> pending() {
        return Map.of(
                "wafar", workflowService.getPendingEntityIds("STUDENT_FINANCE_WAFAR"),
                "finance", workflowService.getPendingEntityIds("STUDENT_FINANCE"));
    }

    @GetMapping("/")
    public ResponseEntity<List<StudentFinanceWafar>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentFinanceWafar> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping("/")
    @PreAuthorize(MoheSecurityExpressions.WAFAR_WRITE)
    public ResponseEntity<?> create(@Valid @RequestBody StudentFinanceWafar entity) {
        try {
            Long userId = SecurityRequestUtils.requireUserId();
            approvalIntegrationService.createStudentFinanceWafarRequest(
                    RequestType.INSERT, entity, null, userId);
            return ResponseEntity.ok(Map.of(
                    "message", "Wafar request submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a balance 409, a 403 or 404 keep their status (R5-19)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.WAFAR_WRITE)
    public ResponseEntity<?> update(@PathVariable Integer id, @Valid @RequestBody StudentFinanceWafar entity) {
        try {
            Long userId = SecurityRequestUtils.requireUserId();
            StudentFinanceWafar original = service.getById(id);
            entity.setId(id);
            approvalIntegrationService.createStudentFinanceWafarRequest(
                    RequestType.UPDATE, entity, original, userId);
            return ResponseEntity.ok(Map.of(
                    "message", "Wafar update submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a balance 409, a 403 or 404 keep their status (R5-19)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.WAFAR_WRITE)
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        try {
            Long userId = SecurityRequestUtils.requireUserId();
            StudentFinanceWafar original = service.getById(id);
            approvalIntegrationService.createStudentFinanceWafarRequest(
                    RequestType.DELETE, original, original, userId);
            return ResponseEntity.ok(Map.of(
                    "message", "Wafar deletion submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a balance 409, a 403 or 404 keep their status (R5-19)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
