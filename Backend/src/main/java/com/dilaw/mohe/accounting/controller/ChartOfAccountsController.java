package com.dilaw.mohe.accounting.controller;

import com.dilaw.mohe.accounting.model.CoaAccount;
import com.dilaw.mohe.accounting.model.CoaLevel;
import com.dilaw.mohe.accounting.model.CoaSegment;
import com.dilaw.mohe.accounting.repository.CoaLevelRepository;
import com.dilaw.mohe.accounting.repository.CoaSegmentRepository;
import com.dilaw.mohe.accounting.service.CoaAccountService;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * The chart of accounts (الدليل المحاسبي).
 *
 * <p>Reading is open to any finance user; editing needs {@code COA.EDIT},
 * because the chart is a controlled document whose codes come from the
 * الدليل المحاسبي الموحد rather than from whoever has the screen open.
 *
 * <p>There is no delete endpoint, and that is not an omission. An account that
 * has been posted to must survive for the audit trail, so
 * {@link CoaAccountService#deactivate} is the only way to retire one.
 */
@RestController
@RequestMapping("api/v1/accounting/chart")
@PreAuthorize(MoheSecurityExpressions.ACCOUNTING_VIEW)
public class ChartOfAccountsController {

    @Autowired private CoaAccountService accountService;
    @Autowired private CoaSegmentRepository segmentRepository;
    @Autowired private CoaLevelRepository levelRepository;

    /** The segments an account code is composed of. */
    @GetMapping("/segments")
    public ResponseEntity<List<CoaSegment>> segments() {
        return ResponseEntity.ok(segmentRepository.findAll());
    }

    /**
     * The levels of each segment: باب، بند، نوع، مجموعة، حساب.
     *
     * <p>Returned rather than hard-coded in the client, because the Executive
     * Regulation defines the names and widths and may change them.
     */
    @GetMapping("/levels")
    public ResponseEntity<List<CoaLevel>> levels() {
        return ResponseEntity.ok(levelRepository.findAll());
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<CoaAccount>> accounts(
            @RequestParam Integer fiscalYearId,
            @RequestParam(required = false) Integer segmentId) {
        return ResponseEntity.ok(segmentId == null
                ? accountService.forYear(fiscalYearId)
                : accountService.forSegment(segmentId, fiscalYearId));
    }

    /** Leaves that may receive postings — what the ledger round will offer. */
    @GetMapping("/accounts/postable")
    public ResponseEntity<List<CoaAccount>> postable(@RequestParam Integer fiscalYearId) {
        return ResponseEntity.ok(accountService.postableAccounts(fiscalYearId));
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<CoaAccount> byId(@PathVariable Integer id) {
        return ResponseEntity.ok(accountService.require(id));
    }

    /** How many accounts exist at each level. Zero everywhere on a fresh install. */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(@RequestParam Integer fiscalYearId) {
        List<CoaAccount> all = accountService.forYear(fiscalYearId);
        return ResponseEntity.ok(Map.of(
                "fiscalYearId", fiscalYearId,
                "accountCount", all.size(),
                "postableCount", all.stream().filter(a -> Boolean.TRUE.equals(a.getPostable())).count(),
                "byLevel", accountService.countByLevel(fiscalYearId)));
    }

    @PostMapping("/accounts")
    @PreAuthorize(MoheSecurityExpressions.COA_EDIT)
    public ResponseEntity<CoaAccount> create(@Valid @RequestBody CoaAccount draft) {
        return ResponseEntity.ok(accountService.create(draft));
    }

    @PutMapping("/accounts/{id}")
    @PreAuthorize(MoheSecurityExpressions.COA_EDIT)
    public ResponseEntity<CoaAccount> update(@PathVariable Integer id, @Valid @RequestBody CoaAccount changes) {
        return ResponseEntity.ok(accountService.update(id, changes));
    }

    /** Retires an account without destroying it. See the class javadoc. */
    @PostMapping("/accounts/{id}/deactivate")
    @PreAuthorize(MoheSecurityExpressions.COA_EDIT)
    public ResponseEntity<CoaAccount> deactivate(@PathVariable Integer id) {
        return ResponseEntity.ok(accountService.deactivate(id));
    }
}
