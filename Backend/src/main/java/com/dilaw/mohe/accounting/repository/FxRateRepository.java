package com.dilaw.mohe.accounting.repository;

import com.dilaw.mohe.accounting.model.FxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FxRateRepository extends JpaRepository<FxRate, Integer> {

    List<FxRate> findAllByOrderByRateDateDescFromCurrencyIdAscToCurrencyIdAsc();

    /** The unique key: one rate per pair per day ({@code UX_FX_RATE_PAIR_DATE}). */
    Optional<FxRate> findByFromCurrencyIdAndToCurrencyIdAndRateDate(
            Integer fromCurrencyId, Integer toCurrencyId, LocalDate rateDate);

    /** The rate in force on a day: the latest one dated on or before it. */
    Optional<FxRate> findFirstByFromCurrencyIdAndToCurrencyIdAndRateDateLessThanEqualOrderByRateDateDesc(
            Integer fromCurrencyId, Integer toCurrencyId, LocalDate on);
}
