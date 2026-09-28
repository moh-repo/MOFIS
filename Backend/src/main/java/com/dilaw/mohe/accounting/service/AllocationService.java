package com.dilaw.mohe.accounting.service;

import com.dilaw.mohe.accounting.model.AttacheAllocation;
import com.dilaw.mohe.accounting.repository.AttacheAllocationRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Quarterly transfers of budget to the cultural attachés.
 *
 * <p>Two kinds, and the difference matters. A {@code REGULAR} allocation is the
 * quarter's planned transfer. An {@code ADDITIONAL} one is applied later in the
 * same quarter and reaches back over students who were added, edited or
 * corrected after the regular transfer went out — so it is a row in its own
 * right, not an adjustment to the original. Reporting has to be able to say how
 * much of a quarter arrived late and why.
 *
 * <p>Amounts are always positive. A downward correction is not a negative
 * allocation: the cash is already at the attaché, so what it produces is a Wafar
 * earmark. See {@link com.dilaw.mohe.accounting.model.WafarEarmark}.
 */
@Service
public class AllocationService {

    @Autowired private AttacheAllocationRepository allocationRepository;
    @Autowired private AdvanceBalanceService advanceBalanceService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CountryScopeService countryScopeService;
    @Autowired private com.dilaw.mohe.security.policy.AccessControl accessControl;
    @Autowired private FiscalPeriodService fiscalPeriodService;
    @Autowired private AttacheCountryValidator attacheCountries;

    /** A transfer belongs to the attaché that received it, so only that attaché reads it. */
    public List<AttacheAllocation> forQuarter(Integer moheQuarterKey) {
        CountryScope scope = countryScopeService.getScope();
        List<AttacheAllocation> all =
                allocationRepository.findByMoheQuarterKeyOrderByStudyCountryInIdAsc(moheQuarterKey);
        if (scope.isMinistryUser()) return all;
        List<Integer> allowed = scope.getAllowedCountryIds();
        return all.stream().filter(a -> allowed.contains(a.getStudyCountryInId())).toList();
    }

    /**
     * One allocation by id, and the gate for approve and transfer.
     *
     * <p>Not found rather than forbidden — allocation ids are sequential, and a
     * 403 would say how much every other attaché received simply by existing.
     */
    public AttacheAllocation require(Integer id) {
        AttacheAllocation allocation = allocationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Allocation not found"));
        if (!countryScopeService.canAccessCountry(allocation.getStudyCountryInId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Allocation not found");
        }
        return allocation;
    }

    /**
     * Drafts an allocation from the fields a maker may choose.
     *
     * <p>FIN-05: the body is copied onto a new row, never saved as sent. It binds the
     * entity, id and status included, and a non-null id made {@code save} a merge:
     * POSTing the id of a TRANSFERRED allocation reset it to DRAFT, and transferring
     * it again booked the advance twice. Same approach as {@code FxRateService.create}.
     */
    @Transactional
    public AttacheAllocation create(AttacheAllocation body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The allocation is required.");
        }
        AttacheAllocation draft = new AttacheAllocation();
        draft.setMoheQuarterKey(body.getMoheQuarterKey());
        draft.setStudyCountryInId(body.getStudyCountryInId());
        draft.setAllocationType(body.getAllocationType());
        draft.setFeeTypeId(body.getFeeTypeId());
        draft.setCurrencyId(body.getCurrencyId());
        draft.setAmount(body.getAmount());
        draft.setNotes(body.getNotes());
        if (draft.getAmount() == null || draft.getAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An allocation must be a positive amount. A downward correction becomes a Wafar "
                            + "earmark instead, because the money is already at the attaché.");
        }
        if (!AttacheAllocation.REGULAR.equals(draft.getAllocationType())
                && !AttacheAllocation.ADDITIONAL.equals(draft.getAllocationType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Allocation type must be REGULAR or ADDITIONAL.");
        }
        if (draft.getStudyCountryInId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The attaché receiving this allocation is required (studyCountryInId).");
        }
        if (draft.getMoheQuarterKey() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The MOHE quarter this allocation belongs to is required (moheQuarterKey).");
        }
        attacheCountries.requireActive(draft.getStudyCountryInId());
        fiscalPeriodService.requireMoheQuarter(draft.getMoheQuarterKey()); // R5-20
        // The fiscal year follows from the quarter (YYYYQ), so the screen need not
        // send both and cannot send a pair that disagree. FIN-13: always — a year the
        // client sent was kept even when it contradicted the quarter.
        draft.setFiscalYearId(fiscalPeriodService.requireYearOfQuarter(draft.getMoheQuarterKey()).getId());
        fiscalPeriodService.assertYearOpen(draft.getFiscalYearId()); // FIN-12
        // Forbidden, not 404, and deliberately different from require(): the caller
        // named this attaché themselves, so there is nothing to conceal, and a
        // silent 404 on a create would read as the record failing to save.
        if (!countryScopeService.canAccessCountry(draft.getStudyCountryInId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Allocating to an attaché outside your scope is not permitted.");
        }
        draft.setStatus(AttacheAllocation.DRAFT);
        // The maker is whoever is signed in, not whatever the body says: approve()
        // compares against this to keep the maker and the checker apart.
        countryScopeService.getCurrentUserId().ifPresent(uid -> draft.setCreatedBy(uid.intValue()));
        return allocationRepository.save(draft);
    }

    /**
     * Approve a draft allocation.
     *
     * <p>Maker and checker must be two people. {@code ALLOCATION_APPROVE} is a
     * different authority from {@code ALLOCATION_CREATE} so that the split is
     * <em>possible</em>; this check is what makes it <em>real</em> when one
     * account happens to hold both — which today every administrator does. The
     * officer who drafted the transfer cannot be the one who releases it, and
     * there is no super-admin exemption: the rule exists precisely for the
     * accounts that can do everything else.
     */
    @Transactional
    public AttacheAllocation approve(Integer id, Integer userId) {
        AttacheAllocation allocation = require(id);
        if (!AttacheAllocation.DRAFT.equals(allocation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a draft allocation can be approved; this one is " + allocation.getStatus() + ".");
        }
        // R-16: MakerCheckerPolicy — the signed-in checker is not the maker; no exemptions.
        accessControl.assertAllowed(com.dilaw.mohe.security.policy.MakerChecker.allocation(allocation.getCreatedBy(), userId), "APPROVE");
        fiscalPeriodService.assertYearOpen(allocation.getFiscalYearId()); // FIN-12
        allocation.setStatus(AttacheAllocation.APPROVED);
        allocation.setUpdatedBy(userId);
        allocation.setUpdatedAt(Instant.now());
        return allocationRepository.save(allocation);
    }

    /**
     * Records that the money has actually left for the attaché.
     *
     * <p>This is the point the advance grows — not on approval. An approved
     * allocation is a decision; a transferred one is cash the attaché can spend,
     * and only the second may be drawn against.
     *
     * <p>FIN-03: the status change is flushed before the advance moves, so of two
     * concurrent transfers of the same allocation the second fails on the row version
     * (409) and books nothing. FIN-12: the transfer date must fall in an open period.
     */
    @Transactional
    public AttacheAllocation markTransferred(Integer id, LocalDate transferDate, String reference) {
        AttacheAllocation allocation = require(id);
        if (!AttacheAllocation.APPROVED.equals(allocation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An allocation must be approved before it is transferred; this one is "
                            + allocation.getStatus() + ".");
        }
        LocalDate on = transferDate == null ? LocalDate.now() : transferDate;
        fiscalPeriodService.assertYearOpen(allocation.getFiscalYearId());
        fiscalPeriodService.assertPeriodOpen(on);
        allocation.setStatus(AttacheAllocation.TRANSFERRED);
        allocation.setTransferDate(on);
        allocation.setTransferReference(reference);
        allocation.setUpdatedAt(Instant.now());
        AttacheAllocation saved = allocationRepository.saveAndFlush(allocation);

        advanceBalanceService.recordTransfer(
                saved.getFiscalYearId(), saved.getStudyCountryInId(),
                saved.getCurrencyId(), saved.getAmount());

        rollUpToQuarter(saved.getMoheQuarterKey());
        return saved;
    }

    /**
     * Sums the quarter's transfers into {@code MOHECORE.MOHE_QUARTER}.
     *
     * <p>This is the summation that has never happened. Every row of MOHE_QUARTER
     * currently has {@code QUARTER_AMOUNT = 0} and {@code BUDGET_AMOUNT = NULL} —
     * the columns exist, and nothing has ever written to them.
     *
     * <p>Deliberately recomputed from the allocations rather than incremented, so
     * a re-run produces the same answer as a first run. An incrementing update
     * that is applied twice is exactly the class of bug the V038 uniqueness
     * constraint exists to prevent elsewhere.
     *
     * <p>Written with JDBC because MOHE_QUARTER has no JPA entity in this module
     * and adding one to reach two columns would be a larger change than the job
     * needs.
     */
    @Transactional
    public BigDecimal rollUpToQuarter(Integer moheQuarterKey) {
        BigDecimal total = allocationRepository.sumTransferredForQuarter(moheQuarterKey);
        jdbcTemplate.update(
                "UPDATE MOHECORE.MOHE_QUARTER SET BUDGET_AMOUNT = ? WHERE QUARTER = ?",
                total, moheQuarterKey);
        return total;
    }

    /** What has actually reached one attaché this year, in one currency. */
    public BigDecimal transferredTo(Integer fiscalYearId, Integer countryId, Integer currencyId) {
        return allocationRepository.sumTransferred(fiscalYearId, countryId, currencyId);
    }
}
