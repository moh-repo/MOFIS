package com.dilaw.mohe.accounting.controller;

import com.dilaw.mohe.accounting.dto.AppropriationView;
import com.dilaw.mohe.accounting.model.BudgetAppropriation;
import com.dilaw.mohe.accounting.service.BudgetAppropriationService;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The annual appropriation from MOF, by stream. Writes need {@code BUDGET.EDIT} (V063). */
@RestController
@RequestMapping("api/v1/accounting/appropriations")
@PreAuthorize(MoheSecurityExpressions.ACCOUNTING_VIEW)
public class BudgetAppropriationController {

    @Autowired private BudgetAppropriationService appropriationService;

    @GetMapping
    public ResponseEntity<List<AppropriationView>> forYear(@RequestParam Integer fiscalYearId) {
        return ResponseEntity.ok(appropriationService.forYear(fiscalYearId));
    }

    @PostMapping
    @PreAuthorize(MoheSecurityExpressions.BUDGET_EDIT)
    public ResponseEntity<BudgetAppropriation> create(@Valid @RequestBody BudgetAppropriation draft) {
        return ResponseEntity.ok(appropriationService.create(draft));
    }

    /** Edits a draft in place; revises an approved figure by opening a new draft. */
    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.BUDGET_EDIT)
    public ResponseEntity<BudgetAppropriation> update(@PathVariable Integer id,
                                                      @Valid @RequestBody BudgetAppropriation changes) {
        return ResponseEntity.ok(appropriationService.update(id, changes));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(MoheSecurityExpressions.BUDGET_EDIT)
    public ResponseEntity<BudgetAppropriation> approve(@PathVariable Integer id) {
        int approver = (int) SecurityRequestUtils.requireUserId();
        return ResponseEntity.ok(appropriationService.approve(id, approver));
    }
}
