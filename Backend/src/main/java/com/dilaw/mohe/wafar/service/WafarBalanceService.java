package com.dilaw.mohe.wafar.service;

import com.dilaw.mohe.codes.WafarSpendingStatus;
import com.dilaw.mohe.exception.LocalizedStatusException;
import com.dilaw.mohe.repository.StudentFinanceWafarRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import com.dilaw.mohe.wafar.dto.WafarBalanceSummaryDto;
import com.dilaw.mohe.wafar.repository.WafarSpendingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/**
 * The Wafar balance card, per attaché rather than per ministry.
 *
 * <p>It used to return one figure: every deposit in the database minus every
 * approved spend, with no country dimension at all. That number implies Cairo's
 * savings can pay Riyadh's students, which is the one thing Wafar cannot do — it
 * is budget already transferred to a particular attaché and it stays there.
 *
 * <p>Both halves are now scoped, and they have to be scoped the same way or the
 * subtraction is meaningless: deposits filtered to Cairo minus spending filtered
 * to nothing would read as a surplus that does not exist.
 *
 * <p>The currency dimension is still missing — the amounts are summed across USD,
 * GBP and SAR as though they were one unit. That belongs with the FX work,
 * not here.
 */
@Service
public class WafarBalanceService {

    @Autowired
    private StudentFinanceWafarRepository depositRepository;

    @Autowired
    private WafarSpendingRepository spendingRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    public WafarBalanceSummaryDto getSummary() {
        CountryScope scope = countryScopeService.getScope();

        // No countries assigned: no balance to report. Zero, not the ministry total.
        if (!scope.isMinistryUser() && scope.getAllowedCountryIds().isEmpty()) {
            return new WafarBalanceSummaryDto(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L);
        }

        boolean unscoped = scope.isMinistryUser();
        // Never empty, and ignored entirely when unscoped.
        List<Integer> countryIds = unscoped ? List.of(-1) : scope.getAllowedCountryIds();

        BigDecimal totalDeposits = depositRepository.sumTotalDeposits(unscoped, countryIds);
        BigDecimal totalSpent = spendingRepository.sumByStatus(WafarSpendingStatus.APPROVED, unscoped, countryIds);
        BigDecimal pendingAmount = spendingRepository.sumByStatus(WafarSpendingStatus.PENDING, unscoped, countryIds);
        BigDecimal currentBalance = totalDeposits.subtract(totalSpent);
        long count = spendingRepository.countAll(WafarSpendingStatus.ALL, unscoped, countryIds);

        return new WafarBalanceSummaryDto(totalDeposits, totalSpent, currentBalance, pendingAmount, count);
    }

    /**
     * What one attaché can still spend: its deposits minus its approved spends (live QA
     * M1, decision D6-a; pending spends are not counted).
     *
     * <p>The spend's own attaché, not the caller's scope: a ministry approver sees every
     * country, and {@link #getSummary()} would have checked the ministry total. Still one
     * figure per attaché, not per currency: neither WAFAR_SPENDING nor
     * STUDENT_FINANCE_WAFAR records a currency (FIX-PLAN D22).
     */
    public BigDecimal availableFor(Integer studyCountryInId) {
        List<Integer> one = List.of(studyCountryInId);
        BigDecimal deposits = depositRepository.sumTotalDeposits(false, one);
        BigDecimal spent = spendingRepository.sumByStatus(WafarSpendingStatus.APPROVED, false, one);
        return (deposits == null ? BigDecimal.ZERO : deposits).subtract(spent == null ? BigDecimal.ZERO : spent);
    }

    /**
     * Refuses (409) a spend the attaché's Wafar does not cover. A spend that names no
     * attaché has no balance to be drawn from and is refused too: the eleven such rows on
     * file predate the rule that every spend names one.
     */
    public void requireCovers(Integer studyCountryInId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return;
        if (studyCountryInId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This spend names no attaché, so there is no Wafar balance to draw it from. Correct it first.");
        }
        BigDecimal available = availableFor(studyCountryInId);
        if (amount.compareTo(available) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Insufficient Wafar balance at this attaché: available " + available.toPlainString()
                            + ", required " + amount.toPlainString() + ".");
        }
    }

    /**
     * Refuses (409) taking {@code decrease} of Wafar away from an attaché when its approved
     * spending already uses it: a deposit deleted, lowered or moved to another attaché
     * (round-5 QA R5-04, the M1 gap: deleting Jordan's approved deposit of 100 left its
     * balance at −10.00). The deposit being changed is still counted in
     * {@link #availableFor}, so what may go is at most that figure.
     */
    public void requireWithdrawable(Integer studyCountryInId, BigDecimal decrease) {
        if (studyCountryInId == null || decrease == null || decrease.signum() <= 0) return;
        BigDecimal available = availableFor(studyCountryInId);
        if (decrease.compareTo(available) > 0) {
            String taken = decrease.toPlainString();
            String left = available.max(BigDecimal.ZERO).toPlainString();
            throw new LocalizedStatusException(HttpStatus.CONFLICT,
                    "This change takes " + taken + " of Wafar away from this attaché, but only " + left
                            + " of its Wafar is not already used by approved spending.",
                    "wafar.deposit.notCovered", taken, left);
        }
    }
}
