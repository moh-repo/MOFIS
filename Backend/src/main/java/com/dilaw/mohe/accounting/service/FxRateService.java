package com.dilaw.mohe.accounting.service;

import com.dilaw.mohe.accounting.model.FxRate;
import com.dilaw.mohe.accounting.repository.FxRateRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Exchange rates by day.
 *
 * <p>Reference data, not tenant data: {@code FX_RATE} has no country column and
 * is deliberately outside the tenant policy, because a rate is the same in Cairo
 * and in Amman. Reads are open to any accounting user; writes need
 * {@code COA.EDIT}, the same controlled-document authority as the chart.
 *
 * <p>There is no "deactivate": the table has no such flag, and a rate is not a
 * document that gets retired — it is either the rate for that day or a mistake.
 * A mistake is deleted. Nothing references {@code FX_RATE} by key, so a delete
 * orphans nothing; the ledger round, when it translates amounts, will store the
 * rate it used on the entry rather than point at this table.
 *
 * <p>No rate is ever invented. {@link #effective} answers empty rather than
 * falling back to 1.0, because a missing rate stops a translation and a made-up
 * one silently misstates it (V039).
 */
@Service
@Transactional(readOnly = true)
public class FxRateService {

    @Autowired private FxRateRepository rateRepository;
    @Autowired private CountryScopeService countryScopeService;

    /**
     * Rates, newest first, narrowed by whichever filters are given.
     *
     * <p>Filtered in memory: the table is small (it ships empty and gains one row
     * per pair per day), and a JPQL query with three optional parameters costs
     * more in typed-null handling on SQL Server than it saves.
     *
     * @param on keep only rates dated on or before this day
     */
    public List<FxRate> list(Integer fromCurrencyId, Integer toCurrencyId, LocalDate on) {
        return rateRepository.findAllByOrderByRateDateDescFromCurrencyIdAscToCurrencyIdAsc().stream()
                .filter(r -> fromCurrencyId == null || fromCurrencyId.equals(r.getFromCurrencyId()))
                .filter(r -> toCurrencyId == null || toCurrencyId.equals(r.getToCurrencyId()))
                .filter(r -> on == null || !r.getRateDate().isAfter(on))
                .toList();
    }

    /** The rate in force on a day: the latest one dated on or before it, if any. */
    public Optional<FxRate> effective(Integer fromCurrencyId, Integer toCurrencyId, LocalDate on) {
        if (fromCurrencyId == null || toCurrencyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both currencies are required.");
        }
        return rateRepository.findFirstByFromCurrencyIdAndToCurrencyIdAndRateDateLessThanEqualOrderByRateDateDesc(
                fromCurrencyId, toCurrencyId, on == null ? LocalDate.now() : on);
    }

    public FxRate require(Integer id) {
        return rateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exchange rate not found"));
    }

    @Transactional
    public FxRate create(FxRate rate) {
        if (rate.getFromCurrencyId() == null || rate.getToCurrencyId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both currencies are required.");
        }
        if (rate.getFromCurrencyId().equals(rate.getToCurrencyId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A currency has no rate against itself.");
        }
        if (rate.getRate() == null || rate.getRate().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rate must be a positive number.");
        }
        if (rate.getRateDate() == null) {
            rate.setRateDate(LocalDate.now());
        }
        // Checked here rather than left to UX_FX_RATE_PAIR_DATE so the answer is a
        // 409 that names the day, not a constraint name in a 500.
        rateRepository.findByFromCurrencyIdAndToCurrencyIdAndRateDate(
                        rate.getFromCurrencyId(), rate.getToCurrencyId(), rate.getRateDate())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "A rate for this pair on " + rate.getRateDate() + " already exists (#"
                                    + existing.getId() + "). Delete it first if it is wrong.");
                });
        rate.setId(null);
        countryScopeService.getCurrentUserId().ifPresent(uid -> rate.setCreatedBy(uid.intValue()));
        return rateRepository.save(rate);
    }

    /** Removes a wrongly entered rate. See the class comment for why this is a delete. */
    @Transactional
    public void delete(Integer id) {
        rateRepository.delete(require(id));
    }
}
