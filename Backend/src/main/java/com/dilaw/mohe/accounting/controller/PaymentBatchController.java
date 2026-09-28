package com.dilaw.mohe.accounting.controller;

import com.dilaw.mohe.accounting.dto.FinanceCandidate;
import com.dilaw.mohe.accounting.dto.PaymentBatchCreateRequest;
import com.dilaw.mohe.accounting.dto.PaymentBatchDetail;
import com.dilaw.mohe.accounting.dto.PaymentBatchSummary;
import com.dilaw.mohe.accounting.model.PaymentBatch;
import com.dilaw.mohe.accounting.service.PaymentBatchService;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Payment batches (الدفعات).
 *
 * <p>Preparing a batch and releasing it are separate authorities, and the
 * approver is always the signed-in user — never a parameter.
 */
@RestController
@RequestMapping("api/v1/accounting/payment-batches")
@PreAuthorize(MoheSecurityExpressions.ACCOUNTING_VIEW)
public class PaymentBatchController {

    @Autowired private PaymentBatchService batchService;

    @GetMapping
    public ResponseEntity<List<PaymentBatchSummary>> forQuarter(
            @RequestParam Integer quarterKey,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(batchService.forQuarter(quarterKey, countryId));
    }

    /** The quarter's finance rows for one attaché, to pick lines from. Declared before {id}. */
    @GetMapping("/candidates")
    public ResponseEntity<List<FinanceCandidate>> candidates(
            @RequestParam Integer quarterKey,
            @RequestParam Integer countryId) {
        return ResponseEntity.ok(batchService.candidates(quarterKey, countryId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentBatchDetail> detail(@PathVariable Integer id) {
        return ResponseEntity.ok(batchService.detail(id));
    }

    @PostMapping
    @PreAuthorize(MoheSecurityExpressions.PAYMENT_BATCH_CREATE)
    public ResponseEntity<PaymentBatchDetail> create(@Valid @RequestBody PaymentBatchCreateRequest request) {
        return ResponseEntity.ok(batchService.create(request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(MoheSecurityExpressions.PAYMENT_BATCH_APPROVE)
    public ResponseEntity<PaymentBatch> approve(@PathVariable Integer id) {
        int approver = (int) SecurityRequestUtils.requireUserId();
        return ResponseEntity.ok(batchService.approve(id, approver));
    }

    /** The point the advance shrinks: the money has gone out. */
    @PostMapping("/{id}/pay")
    @PreAuthorize(MoheSecurityExpressions.PAYMENT_BATCH_APPROVE)
    public ResponseEntity<PaymentBatch> pay(@PathVariable Integer id) {
        int payer = (int) SecurityRequestUtils.requireUserId();
        return ResponseEntity.ok(batchService.markPaid(id, payer));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(MoheSecurityExpressions.PAYMENT_BATCH_APPROVE)
    public ResponseEntity<PaymentBatch> cancel(@PathVariable Integer id) {
        int user = (int) SecurityRequestUtils.requireUserId();
        return ResponseEntity.ok(batchService.cancel(id, user));
    }
}
