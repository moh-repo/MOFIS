package com.dilaw.mohe.wafar.controller;

import java.util.Map;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.wafar.dto.*;
import com.dilaw.mohe.wafar.service.WafarBalanceService;
import com.dilaw.mohe.wafar.service.WafarSpendingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * WAFAR (savings) balances and spending.
 *
 * <p>These ten endpoints carried no authorisation of their own. {@code /api/**}
 * requires a valid JWT, so they were never anonymous — but any authenticated
 * user reached them, including approving and rejecting spending transactions and
 * deleting them. Every comparable controller was already guarded:
 * {@code StudentFinanceWafarController} with the same expression used here, and
 * {@code WafarReconciliationController} with {@code FINANCE}.
 *
 * <p>Since the 27 Aug 2026 pass (plan item 1.1) the class-level {@code WAFAR}
 * gate covers reads only. It admits {@code WAFAR.VIEW} and {@code WAFAR.EXPORT},
 * which must not be enough to spend or to approve spending, so each write
 * carries {@code WAFAR_WRITE} and each decision {@code WAFAR_APPROVE}. A
 * method-level annotation replaces the class-level one, so those constants
 * stand on their own.
 */
@RestController
@RequestMapping("api/v1/wafar")
@PreAuthorize(MoheSecurityExpressions.WAFAR)
public class WafarController {

    @Autowired
    private WafarBalanceService balanceService;

    @Autowired
    private WafarSpendingService spendingService;

    @GetMapping("/balance")
    public ResponseEntity<WafarBalanceSummaryDto> getBalance() {
        return ResponseEntity.ok(balanceService.getSummary());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<WafarSpendingCategoryDto>> getCategories() {
        return ResponseEntity.ok(spendingService.getActiveCategories());
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<WafarSpendingDto>> getTransactions(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(spendingService.getAll(categoryId, status, studentId, dateFrom, dateTo));
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<WafarSpendingDto> getTransaction(@PathVariable Integer id) {
        return ResponseEntity.ok(spendingService.getById(id));
    }

    /** The maker is the signed-in user, never a request parameter (it used to be {@code ?createdBy=}). */
    @PostMapping("/transactions")
    @PreAuthorize(MoheSecurityExpressions.WAFAR_WRITE)
    public ResponseEntity<WafarSpendingDto> createTransaction(@Valid @RequestBody WafarSpendingCreateDto dto) {
        return ResponseEntity.ok(spendingService.create(dto));
    }

    @PutMapping("/transactions/{id}")
    @PreAuthorize(MoheSecurityExpressions.WAFAR_WRITE)
    public ResponseEntity<WafarSpendingDto> updateTransaction(
            @PathVariable Integer id,
            @Valid @RequestBody WafarSpendingCreateDto dto) {
        return ResponseEntity.ok(spendingService.update(id, dto));
    }

    /** The approver is the signed-in user, never a request parameter. */
    /**
     * N-04.e: a ministry account proposes a correction; the attaché's
     * administrator approves it in the approval inbox and the change is applied
     * as them. 202 with the request id.
     */
    @PostMapping("/transactions/{id}/correction-requests")
    public ResponseEntity<Map<String, Object>> requestCorrection(@PathVariable Integer id,
                                                                 @Valid @RequestBody com.dilaw.mohe.wafar.dto.WafarCorrectionRequest body) {
        com.dilaw.mohe.approval.model.ApprovalRequest request = spendingService.requestCorrection(id, body);
        return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED).body(Map.of(
                "status", "PENDING_APPROVAL",
                "requestId", request.getId(),
                "message", "The correction was sent to the attaché's administrator for approval."));
    }

    @PutMapping("/transactions/{id}/approve")
    @PreAuthorize(MoheSecurityExpressions.WAFAR_APPROVE)
    public ResponseEntity<WafarSpendingDto> approveTransaction(@PathVariable Integer id) {
        return ResponseEntity.ok(spendingService.approve(id));
    }

    @PutMapping("/transactions/{id}/reject")
    @PreAuthorize(MoheSecurityExpressions.WAFAR_APPROVE)
    public ResponseEntity<WafarSpendingDto> rejectTransaction(
            @PathVariable Integer id,
            @Valid @RequestBody WafarRejectRequest body) {
        return ResponseEntity.ok(spendingService.reject(id, body.reason()));
    }

    @DeleteMapping("/transactions/{id}")
    @PreAuthorize(MoheSecurityExpressions.WAFAR_WRITE)
    public ResponseEntity<Void> cancelTransaction(@PathVariable Integer id) {
        spendingService.cancel(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/student-fee-payments/{studentId}")
    public ResponseEntity<List<WafarStudentFeePaymentDto>> getStudentFeePayments(@PathVariable Integer studentId) {
        return ResponseEntity.ok(spendingService.getStudentFeePayments(studentId));
    }
}
