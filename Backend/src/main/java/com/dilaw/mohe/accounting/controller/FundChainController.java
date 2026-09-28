package com.dilaw.mohe.accounting.controller;

import com.dilaw.mohe.accounting.model.AttacheAdvanceBalance;
import com.dilaw.mohe.accounting.model.AttacheAllocation;
import com.dilaw.mohe.accounting.model.FiscalPeriod;
import com.dilaw.mohe.accounting.model.FeeType;
import com.dilaw.mohe.accounting.model.FiscalYear;
import com.dilaw.mohe.accounting.repository.FeeTypeRepository;
import com.dilaw.mohe.accounting.service.AdvanceBalanceService;
import com.dilaw.mohe.accounting.service.AllocationService;
import com.dilaw.mohe.accounting.service.FiscalPeriodService;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The fund chain: fiscal calendar, quarterly allocations, and attaché advances.
 *
 * <p>Creating an allocation and approving one are separate authorities on
 * purpose — the officer who proposes a transfer should not be the one who
 * releases it.
 */
@RestController
@RequestMapping("api/v1/accounting")
@PreAuthorize(MoheSecurityExpressions.ACCOUNTING_VIEW)
public class FundChainController {

    @Autowired private FiscalPeriodService fiscalPeriodService;
    @Autowired private AllocationService allocationService;
    @Autowired private AdvanceBalanceService advanceBalanceService;
    @Autowired private FeeTypeRepository feeTypeRepository;

    // --------------------------------------------------------------- streams

    /** The streams the budget is broken into: stipend, books, travel, university fees, other. */
    @GetMapping("/fee-types")
    public ResponseEntity<List<FeeType>> feeTypes() {
        return ResponseEntity.ok(feeTypeRepository.findAll());
    }

    // ---------------------------------------------------------------- calendar

    @GetMapping("/fiscal-years")
    public ResponseEntity<List<FiscalYear>> fiscalYears() {
        return ResponseEntity.ok(fiscalPeriodService.years());
    }

    @GetMapping("/fiscal-years/{id}/periods")
    public ResponseEntity<List<FiscalPeriod>> periods(@PathVariable Integer id) {
        return ResponseEntity.ok(fiscalPeriodService.periodsOf(id));
    }

    /** Opens a fiscal year with its 13 periods; {@code {"yearNo": 2027}}. */
    @PostMapping("/fiscal-years")
    @PreAuthorize(MoheSecurityExpressions.FISCAL_YEAR_ADD)
    public ResponseEntity<FiscalYear> openYear(@RequestBody Map<String, Integer> body) {
        int opener = (int) SecurityRequestUtils.requireUserId();
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(fiscalPeriodService.openYear(body == null ? null : body.get("yearNo"), opener));
    }

    @PostMapping("/periods/{id}/close")
    @PreAuthorize(MoheSecurityExpressions.FISCAL_PERIOD_CLOSE)
    public ResponseEntity<FiscalPeriod> closePeriod(@PathVariable Integer id,
                                                    @RequestParam(required = false) Integer userId) {
        return ResponseEntity.ok(fiscalPeriodService.closePeriod(id, userId));
    }

    /**
     * Closes a year and every period in it.
     *
     * <p>One-way. Reopening needs {@code SESSION_CONTEXT('fincore_allow_reopen')},
     * which no endpoint sets — it is a deliberate administrative act.
     */
    @PostMapping("/fiscal-years/{id}/close")
    @PreAuthorize(MoheSecurityExpressions.FISCAL_PERIOD_CLOSE)
    public ResponseEntity<FiscalYear> closeYear(@PathVariable Integer id,
                                                @RequestParam(required = false) Integer userId) {
        return ResponseEntity.ok(fiscalPeriodService.closeYear(id, userId));
    }

    // ------------------------------------------------------------ allocations

    @GetMapping("/allocations")
    public ResponseEntity<List<AttacheAllocation>> allocations(@RequestParam Integer quarterKey) {
        return ResponseEntity.ok(allocationService.forQuarter(quarterKey));
    }

    @PostMapping("/allocations")
    @PreAuthorize(MoheSecurityExpressions.ALLOCATION_CREATE)
    public ResponseEntity<AttacheAllocation> create(@RequestBody AttacheAllocation draft) {
        return ResponseEntity.ok(allocationService.create(draft));
    }

    @PostMapping("/allocations/{id}/approve")
    @PreAuthorize(MoheSecurityExpressions.ALLOCATION_APPROVE)
    public ResponseEntity<AttacheAllocation> approve(@PathVariable Integer id) {
        // The approver is the signed-in user, never a parameter — the same
        // correction the Wafar approve/reject endpoints had in August 2026.
        int approver = (int) SecurityRequestUtils.requireUserId();
        return ResponseEntity.ok(allocationService.approve(id, approver));
    }

    /** The point the advance actually grows: cash has left for the attaché. */
    @PostMapping("/allocations/{id}/transfer")
    @PreAuthorize(MoheSecurityExpressions.ALLOCATION_APPROVE)
    public ResponseEntity<AttacheAllocation> transfer(
            @PathVariable Integer id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transferDate,
            @RequestParam(required = false) String reference) {
        return ResponseEntity.ok(allocationService.markTransferred(id, transferDate, reference));
    }

    // --------------------------------------------------------------- advances

    @GetMapping("/advances")
    public ResponseEntity<List<AttacheAdvanceBalance>> advances(@RequestParam Integer fiscalYearId) {
        return ResponseEntity.ok(advanceBalanceService.forYear(fiscalYearId));
    }

    /**
     * Does every stored total still agree with the rows that produced it?
     *
     * <p>An empty list is the healthy answer. Anything in it means a running
     * total and its own detail disagree, and no report built on either can be
     * trusted until it is resolved.
     */
    @GetMapping("/advances/reconciliation")
    public ResponseEntity<Map<String, Object>> reconciliation(@RequestParam Integer fiscalYearId) {
        List<AdvanceBalanceService.Discrepancy> drift = advanceBalanceService.reconcile(fiscalYearId);
        return ResponseEntity.ok(Map.of(
                "fiscalYearId", fiscalYearId,
                "reconciled", drift.isEmpty(),
                "discrepancies", drift));
    }

    /**
     * The cash an attaché held when the year opened.
     *
     * <p>Entered once as a verified figure, never derived: the 2024 archive has
     * no attaché attribution, so splitting it across offices would be invention.
     */
    @PostMapping("/advances/opening-balance")
    @PreAuthorize(MoheSecurityExpressions.ALLOCATION_APPROVE)
    public ResponseEntity<AttacheAdvanceBalance> openingBalance(
            @RequestParam Integer fiscalYearId,
            @RequestParam Integer countryId,
            @RequestParam Integer currencyId,
            @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(
                advanceBalanceService.setOpeningBalance(fiscalYearId, countryId, currencyId, amount));
    }
}
