package com.dilaw.mohe.accounting.service;

import com.dilaw.mohe.accounting.dto.AppropriationView;
import com.dilaw.mohe.accounting.model.AttacheAllocation;
import com.dilaw.mohe.accounting.model.BudgetAppropriation;
import com.dilaw.mohe.accounting.repository.AttacheAllocationRepository;
import com.dilaw.mohe.accounting.repository.BudgetAppropriationRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The annual appropriation from MOF — the head of the fund chain.
 *
 * <p>Status is {@code DRAFT} then {@code APPROVED}, and an approved figure is
 * never edited in place: {@link #update} marks it {@code REVISED} and opens a new
 * draft carrying the change, so the figure that was approved stays on record
 * next to the one that replaced it. {@code UX_APPROP_YEAR_FEE_CUR} excludes
 * revised rows, which is what lets the replacement exist.
 *
 * <p><b>Country scope.</b> {@code STUDY_COUNTRY_IN_ID} is nullable here and null
 * means <em>the whole ministry</em> — the appropriation not broken down by
 * attaché. Those rows are shared with every reader, the way a fiscal year is,
 * because an attaché reading the national budget line reads nothing that is
 * another attaché's. A row that names a country is filtered normally. On the
 * write side only the ministry may produce a null-country row, which is what
 * keeps the permissive read safe.
 */
@Service
@Transactional(readOnly = true)
public class BudgetAppropriationService {

    @Autowired private BudgetAppropriationRepository appropriationRepository;
    @Autowired private com.dilaw.mohe.security.policy.AccessControl accessControl;
    @Autowired private AttacheAllocationRepository allocationRepository;
    @Autowired private CountryScopeService countryScopeService;
    @Autowired private AttacheCountryValidator attacheCountries;

    /** The year's appropriations the caller may see, each with what has been drawn against it. */
    public List<AppropriationView> forYear(Integer fiscalYearId) {
        if (fiscalYearId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fiscalYearId is required");
        }
        CountryScope scope = countryScopeService.getScope();
        List<BudgetAppropriation> rows = appropriationRepository
                .findByFiscalYearIdOrderByFeeTypeIdAscCurrencyIdAscStudyCountryInIdAsc(fiscalYearId).stream()
                .filter(a -> visible(a, scope))
                .toList();
        if (rows.isEmpty()) return List.of();

        // The drawn-down figures are also scoped: a country officer is shown what
        // reached their own attachés against the national line, not the ministry's
        // total — which would be every other attaché's business in aggregate.
        List<Object[]> committed = allocationRepository.sumCommittedByStream(fiscalYearId).stream()
                .filter(r -> scope.isMinistryUser() || scope.getAllowedCountryIds().contains((Integer) r[2]))
                .toList();

        return rows.stream().map(a -> AppropriationView.of(a,
                sum(committed, a, false),
                sum(committed, a, true))).toList();
    }

    /** Not found rather than forbidden: ids are sequential and a 403 confirms what exists. */
    public BudgetAppropriation require(Integer id) {
        BudgetAppropriation a = appropriationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appropriation not found"));
        if (!visible(a, countryScopeService.getScope())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appropriation not found");
        }
        return a;
    }

    @Transactional
    public BudgetAppropriation create(BudgetAppropriation draft) {
        validate(draft);
        BudgetAppropriation a = new BudgetAppropriation();
        a.setFiscalYearId(draft.getFiscalYearId());
        a.setFeeTypeId(draft.getFeeTypeId());
        a.setCurrencyId(draft.getCurrencyId());
        a.setAmount(draft.getAmount());
        a.setStudyCountryInId(resolveCountryForWrite(draft.getStudyCountryInId()));
        attacheCountries.requireActive(a.getStudyCountryInId());
        a.setStatus(BudgetAppropriation.DRAFT);
        a.setReferenceNumber(draft.getReferenceNumber());
        a.setNotes(draft.getNotes());
        countryScopeService.getCurrentUserId().ifPresent(uid -> a.setCreatedBy(uid.intValue()));
        return saveGuarded(a);
    }

    /**
     * Changes the amount, reference or notes.
     *
     * <p>A draft is edited in place. An approved appropriation is not: it becomes
     * {@code REVISED} and a new draft is opened with the change, so the approved
     * figure survives beside its replacement and the replacement goes through
     * approval again. A revised row is history and cannot be touched.
     */
    @Transactional
    public BudgetAppropriation update(Integer id, BudgetAppropriation changes) {
        BudgetAppropriation current = require(id);
        if (changes.getAmount() == null || changes.getAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An appropriation must be a positive amount.");
        }
        Integer userId = countryScopeService.getCurrentUserId().map(Long::intValue).orElse(null);

        if (BudgetAppropriation.DRAFT.equals(current.getStatus())) {
            current.setAmount(changes.getAmount());
            current.setReferenceNumber(changes.getReferenceNumber());
            current.setNotes(changes.getNotes());
            current.setUpdatedBy(userId);
            current.setUpdatedAt(Instant.now());
            return saveGuarded(current);
        }
        if (BudgetAppropriation.APPROVED.equals(current.getStatus())) {
            current.setStatus(BudgetAppropriation.REVISED);
            current.setUpdatedBy(userId);
            current.setUpdatedAt(Instant.now());
            appropriationRepository.save(current);

            BudgetAppropriation replacement = new BudgetAppropriation();
            replacement.setFiscalYearId(current.getFiscalYearId());
            replacement.setFeeTypeId(current.getFeeTypeId());
            replacement.setCurrencyId(current.getCurrencyId());
            replacement.setStudyCountryInId(current.getStudyCountryInId());
            replacement.setAmount(changes.getAmount());
            replacement.setReferenceNumber(changes.getReferenceNumber());
            replacement.setNotes(changes.getNotes());
            replacement.setStatus(BudgetAppropriation.DRAFT);
            replacement.setCreatedBy(userId);
            return saveGuarded(replacement);
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "A revised appropriation is history; edit the draft that replaced it.");
    }

    /**
     * Approves a draft. Maker and checker are two people, as everywhere else in
     * the module: the officer who entered the figure cannot be the one who
     * confirms it, and administrators are not exempt.
     *
     * <p>FIN-10: nor can the officer who last edited the draft. Maker-checker compared
     * only against {@code createdBy}, so A entered 50,000, B changed it to 500,000 and
     * approved their own figure. Editing a draft in place stamps {@code updatedBy}, and
     * a draft's {@code updatedBy} is only ever that edit (approval moves it out of
     * DRAFT), so it names the last editor.
     */
    @Transactional
    public BudgetAppropriation approve(Integer id, Integer userId) {
        BudgetAppropriation a = require(id);
        if (!BudgetAppropriation.DRAFT.equals(a.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a draft appropriation can be approved; this one is " + a.getStatus() + ".");
        }
        // R-16: MakerCheckerPolicy — the signed-in checker is not the maker; no exemptions.
        accessControl.assertAllowed(com.dilaw.mohe.security.policy.MakerChecker.appropriation(a.getCreatedBy(), userId), "APPROVE");
        if (a.getUpdatedBy() != null) {
            accessControl.assertAllowed(new com.dilaw.mohe.security.policy.MakerChecker(
                    "An appropriation", "last edited", a.getUpdatedBy(), userId), "APPROVE");
        }
        a.setStatus(BudgetAppropriation.APPROVED);
        a.setUpdatedBy(userId);
        a.setUpdatedAt(Instant.now());
        return appropriationRepository.save(a);
    }

    // ---------------------------------------------------------------- helpers

    private static boolean visible(BudgetAppropriation a, CountryScope scope) {
        return a.getStudyCountryInId() == null
                || scope.isMinistryUser()
                || scope.getAllowedCountryIds().contains(a.getStudyCountryInId());
    }

    /**
     * Only the ministry may leave the country empty. An officer with one attaché
     * gets theirs; with several they must choose. This is what makes the
     * permissive read of null-country rows safe.
     */
    private Integer resolveCountryForWrite(Integer requested) {
        CountryScope scope = countryScopeService.getScope();
        if (scope.isMinistryUser()) return requested;
        if (requested == null) {
            List<Integer> allowed = scope.getAllowedCountryIds();
            if (allowed.size() == 1) return allowed.get(0);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The attaché this appropriation is for is required (studyCountryInId). "
                            + "Only the ministry may enter a ministry-wide figure.");
        }
        if (!countryScopeService.canAccessCountry(requested)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Entering an appropriation for an attaché outside your scope is not permitted.");
        }
        return requested;
    }

    private static void validate(BudgetAppropriation a) {
        if (a.getFiscalYearId() == null || a.getFeeTypeId() == null || a.getCurrencyId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fiscal year, fee type and currency are required.");
        }
        if (a.getAmount() == null || a.getAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An appropriation must be a positive amount.");
        }
    }

    /**
     * Sums the committed allocations that draw on one appropriation.
     *
     * <p>A ministry-wide row (null country) is drawn by every attaché's
     * allocations for that stream and currency; a row naming an attaché only by
     * that attaché's. Allocations with no fee type are left out — they cannot be
     * attributed to one stream.
     */
    private static BigDecimal sum(List<Object[]> committed, BudgetAppropriation a, boolean transferredOnly) {
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] r : committed) {
            if (!Objects.equals(r[0], a.getFeeTypeId())) continue;
            if (!Objects.equals(r[1], a.getCurrencyId())) continue;
            if (a.getStudyCountryInId() != null && !Objects.equals(r[2], a.getStudyCountryInId())) continue;
            if (transferredOnly && !AttacheAllocation.TRANSFERRED.equals(r[3])) continue;
            total = total.add((BigDecimal) r[4]);
        }
        return total;
    }

    /** One live appropriation per (year, stream, currency, attaché); the index says so, this says it in words. */
    private BudgetAppropriation saveGuarded(BudgetAppropriation a) {
        try {
            return appropriationRepository.saveAndFlush(a);
        } catch (DataIntegrityViolationException ex) {
            for (Throwable t = ex; t != null; t = t.getCause()) {
                if (t.getMessage() != null && t.getMessage().contains("UX_APPROP_YEAR_FEE_CUR")) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "An appropriation for this year, stream, currency and attaché already exists. "
                                    + "Revise it rather than adding a second.", ex);
                }
            }
            throw ex;
        }
    }
}
