package com.dilaw.mohe.accounting.service;

import com.dilaw.mohe.accounting.model.AttacheAdvanceBalance;
import com.dilaw.mohe.accounting.repository.AttacheAdvanceBalanceRepository;
import com.dilaw.mohe.accounting.repository.AttacheAllocationRepository;
import com.dilaw.mohe.accounting.repository.WafarEarmarkRepository;
import com.dilaw.mohe.accounting.repository.WafarUseRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The advance (العهدة) each cultural attaché holds, and the arithmetic on it.
 *
 * <p>The identity this service exists to keep true, per attaché, fiscal year and
 * currency:
 *
 * <pre>
 *   AVAILABLE = OPENING + TRANSFERRED - DISBURSED - RETURNED
 *               - (WAFAR_EARMARKED - WAFAR_SPENT)
 * </pre>
 *
 * <p>The database owns the invariants — {@code CK_ADV_NOT_OVERDRAWN} and
 * {@code CK_ADV_WAFAR_NOT_OVERSPENT} make an impossible state impossible to
 * store, whichever route writes it. This class owns the arithmetic, because
 * running totals maintained by triggers across five source tables would put the
 * hardest sums in the least testable place.
 *
 * <p>The cost of that choice is that stored totals can drift from the rows that
 * produced them, so {@link #reconcile} recomputes from source and reports the
 * difference. A total that disagrees with its own detail is the failure this
 * design has to be able to detect.
 */
@Service
public class AdvanceBalanceService {

    @Autowired private AttacheAdvanceBalanceRepository balanceRepository;
    @Autowired private AttacheAllocationRepository allocationRepository;
    @Autowired private WafarEarmarkRepository earmarkRepository;
    @Autowired private WafarUseRepository useRepository;
    @Autowired private CountryScopeService countryScopeService;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    /** A balance row, created at zero if the attaché has none for this year yet. */
    @Transactional
    public AttacheAdvanceBalance openBalance(Integer fiscalYearId, Integer countryId, Integer currencyId) {
        return balanceRepository
                .findByFiscalYearIdAndStudyCountryInIdAndCurrencyId(fiscalYearId, countryId, currencyId)
                .orElseGet(() -> {
                    AttacheAdvanceBalance created = new AttacheAdvanceBalance();
                    created.setFiscalYearId(fiscalYearId);
                    created.setStudyCountryInId(countryId);
                    created.setCurrencyId(currencyId);
                    return balanceRepository.save(created);
                });
    }

    public AttacheAdvanceBalance require(Integer fiscalYearId, Integer countryId, Integer currencyId) {
        assertMaySee(countryId);
        return balanceRepository
                .findByFiscalYearIdAndStudyCountryInIdAndCurrencyId(fiscalYearId, countryId, currencyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No advance balance for country " + countryId + " in fiscal year " + fiscalYearId));
    }

    /*
     * ── Country scope ─────────────────────────────────────────────────────────
     *
     * An attaché *is* a country here, so an advance balance is the most directly
     * country-owned thing in the module: it is how much cash one office holds and
     * how much of it is already committed.
     *
     * STUDY_COUNTRY_IN_ID is NOT NULL on this table, so there is no shared row to
     * make an exception for — unlike the chart of accounts, where a null country
     * means the unified chart.
     *
     * Only the reads and the one explicit write (setOpeningBalance) are scoped.
     * The record* bookkeeping methods below are deliberately not: they are called
     * by AllocationService and by the derivation, on behalf of the ministry rather
     * than of whoever happens to be logged in, and scoping them would make the
     * fund chain's totals depend on who triggered them.
     */
    private void assertMaySee(Integer countryId) {
        if (!countryScopeService.canAccessCountry(countryId)) {
            // Not found rather than forbidden: an advance is keyed by (year,
            // country, currency), so a 403 would confirm which attachés hold one.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No advance balance for country " + countryId + " in fiscal year");
        }
    }

    public List<AttacheAdvanceBalance> forYear(Integer fiscalYearId) {
        CountryScope scope = countryScopeService.getScope();
        List<AttacheAdvanceBalance> all = balanceRepository.findByFiscalYearId(fiscalYearId);
        if (scope.isMinistryUser()) return all;
        List<Integer> allowed = scope.getAllowedCountryIds();
        return all.stream()
                .filter(b -> allowed.contains(b.getStudyCountryInId()))
                .toList();
    }

    /**
     * Re-reads a balance so its computed columns are current.
     *
     * <p>{@code wafarBalance} and {@code available} are computed by SQL Server,
     * so an entity that has been modified in this transaction still carries the
     * values it was loaded with. Anything that reports those two figures — a
     * screen, a test, a reconciliation — has to reload first or it will show a
     * number that was true a few statements ago.
     */
    @Transactional
    public AttacheAdvanceBalance reload(AttacheAdvanceBalance balance) {
        balanceRepository.flush();
        entityManager.refresh(balance);
        return balance;
    }

    /**
     * Records the opening cash an attaché held when the year began.
     *
     * <p>Entered once, as a verified figure. It is deliberately not derived from
     * the 2024 archive: that data has no attaché attribution at all
     * ({@code STUDY_FINANCE_DOF_ID} is 0 on every one of its 3,764 rows), so any
     * split across offices would be invention.
     */
    @Transactional
    public AttacheAdvanceBalance setOpeningBalance(Integer fiscalYearId, Integer countryId,
                                                   Integer currencyId, BigDecimal opening) {
        // The one write on this service that a user triggers directly, so the one
        // that needs the check. Setting another attaché's opening cash would
        // rewrite the starting point of its whole year.
        assertMaySee(countryId);
        requirePositiveOrZero(opening, "opening balance");
        AttacheAdvanceBalance balance = openBalance(fiscalYearId, countryId, currencyId);
        balance.setOpeningBalance(opening);
        balance.setUpdatedAt(Instant.now());
        return balanceRepository.save(balance);
    }

    /** Money has reached the attaché. */
    @Transactional
    public AttacheAdvanceBalance recordTransfer(Integer fiscalYearId, Integer countryId,
                                                Integer currencyId, BigDecimal amount) {
        requirePositive(amount, "transfer");
        AttacheAdvanceBalance balance = openBalance(fiscalYearId, countryId, currencyId);
        balance.setTransferred(balance.getTransferred().add(amount));
        balance.setUpdatedAt(Instant.now());
        return balanceRepository.save(balance);
    }

    /** Money has left the attaché to a beneficiary, funded by budget. */
    @Transactional
    public AttacheAdvanceBalance recordDisbursement(Integer fiscalYearId, Integer countryId,
                                                    Integer currencyId, BigDecimal amount) {
        requirePositive(amount, "disbursement");
        AttacheAdvanceBalance balance = require(fiscalYearId, countryId, currencyId);
        balance.setDisbursed(balance.getDisbursed().add(amount));
        balance.setUpdatedAt(Instant.now());
        return saveGuarded(balance, "disbursement");
    }

    /**
     * An entitlement was not paid, so it becomes Wafar.
     *
     * <p>No cash moves. This is a re-earmarking of an advance the ministry
     * already holds — Wafar is not new money, and booking it as income would
     * double-count the budget.
     */
    @Transactional
    public AttacheAdvanceBalance recordEarmark(Integer fiscalYearId, Integer countryId,
                                               Integer currencyId, BigDecimal amount) {
        requirePositive(amount, "Wafar earmark");
        AttacheAdvanceBalance balance = require(fiscalYearId, countryId, currencyId);
        balance.setWafarEarmarked(balance.getWafarEarmarked().add(amount));
        balance.setUpdatedAt(Instant.now());
        return saveGuarded(balance, "Wafar earmark");
    }

    /**
     * Wafar was spent on someone else, with permission.
     *
     * <p><b>Two counters move.</b> {@code wafarSpent} rises because the earmark
     * is released, and {@code disbursed} rises because the cash actually leaves.
     * {@code available} is therefore unchanged, which is the right answer: paying
     * a student out of savings does not make the attaché any freer than it was.
     *
     * <p>Moving only one of the two is the mistake this method exists to prevent
     * — it would drift the balance by the value of every Wafar payment ever made.
     * {@code AdvanceIdentityTest} pins the behaviour down.
     */
    @Transactional
    public AttacheAdvanceBalance recordWafarUse(Integer fiscalYearId, Integer countryId,
                                                Integer currencyId, BigDecimal amount) {
        requirePositive(amount, "Wafar use");
        AttacheAdvanceBalance balance = require(fiscalYearId, countryId, currencyId);

        // Computed from the in-memory fields, NOT from getWafarBalance(). That
        // getter maps a database computed column, so it holds whatever was true
        // when the row was loaded — earmark then spend inside one transaction and
        // it is already stale, which would let a use through against a balance
        // that no longer exists. The CHECK constraint would still catch it, but
        // as a 500 rather than the 409 this is.
        BigDecimal held = balance.getWafarEarmarked().subtract(balance.getWafarSpent());
        if (amount.compareTo(held) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Wafar use of " + amount + " exceeds the savings held at that attaché (" + held + ")");
        }

        balance.setWafarSpent(balance.getWafarSpent().add(amount));
        balance.setDisbursed(balance.getDisbursed().add(amount));
        balance.setUpdatedAt(Instant.now());
        return saveGuarded(balance, "Wafar use");
    }

    /** Cash sent back to the ministry. */
    @Transactional
    public AttacheAdvanceBalance recordReturn(Integer fiscalYearId, Integer countryId,
                                              Integer currencyId, BigDecimal amount) {
        requirePositive(amount, "return");
        AttacheAdvanceBalance balance = require(fiscalYearId, countryId, currencyId);
        balance.setReturned(balance.getReturned().add(amount));
        balance.setUpdatedAt(Instant.now());
        return saveGuarded(balance, "return");
    }

    /**
     * Recomputes each figure from the rows that produced it.
     *
     * <p>Every delta must be zero. A non-zero one means the running total and its
     * own detail disagree, which no report built on either can be trusted while
     * it lasts.
     */
    public List<Discrepancy> reconcile(Integer fiscalYearId) {
        // Reconciles what the caller can see. A country officer being told the
        // ministry reconciles is not useful to them; being told their own attaché
        // does not is the whole point of the check.
        return forYear(fiscalYearId).stream()
                .map(this::compare)
                .filter(Discrepancy::hasDrift)
                .toList();
    }

    private Discrepancy compare(AttacheAdvanceBalance b) {
        BigDecimal transferred = allocationRepository.sumTransferred(
                b.getFiscalYearId(), b.getStudyCountryInId(), b.getCurrencyId());
        BigDecimal earmarked = earmarkRepository.sumEarmarked(
                b.getFiscalYearId(), b.getStudyCountryInId(), b.getCurrencyId());
        BigDecimal used = useRepository.sumUsed(
                b.getFiscalYearId(), b.getStudyCountryInId(), b.getCurrencyId());

        return new Discrepancy(
                b.getStudyCountryInId(),
                b.getCurrencyId(),
                b.getTransferred().subtract(transferred),
                b.getWafarEarmarked().subtract(earmarked),
                b.getWafarSpent().subtract(used));
    }

    /**
     * The difference between a stored total and its recomputed value.
     * All three must be zero.
     */
    public record Discrepancy(Integer countryId,
                              Integer currencyId,
                              BigDecimal transferredDelta,
                              BigDecimal earmarkedDelta,
                              BigDecimal spentDelta) {

        public boolean hasDrift() {
            return transferredDelta.signum() != 0
                    || earmarkedDelta.signum() != 0
                    || spentDelta.signum() != 0;
        }
    }

    /**
     * Saves, turning the database's refusal into an answer a caller can act on.
     *
     * <p>{@code CK_ADV_NOT_OVERDRAWN} firing means the attaché was asked to pay
     * out money it never held. That is a 409, not a 500 — the request was
     * coherent, the state simply does not allow it.
     */
    private AttacheAdvanceBalance saveGuarded(AttacheAdvanceBalance balance, String what) {
        try {
            return balanceRepository.saveAndFlush(balance);
        } catch (RuntimeException ex) {
            if (violatesAdvanceInvariant(ex)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This " + what + " would overdraw the attaché's advance for country "
                                + balance.getStudyCountryInId() + ".", ex);
            }
            throw ex;
        }
    }

    private static boolean violatesAdvanceInvariant(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null
                    && (message.contains("CK_ADV_NOT_OVERDRAWN")
                     || message.contains("CK_ADV_WAFAR_NOT_OVERSPENT"))) {
                return true;
            }
        }
        return false;
    }

    private static void requirePositive(BigDecimal amount, String what) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A " + what + " must be a positive amount.");
        }
    }

    private static void requirePositiveOrZero(BigDecimal amount, String what) {
        if (amount == null || amount.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A " + what + " cannot be negative.");
        }
    }
}
