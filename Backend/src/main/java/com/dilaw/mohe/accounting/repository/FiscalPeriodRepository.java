package com.dilaw.mohe.accounting.repository;

import com.dilaw.mohe.accounting.model.FiscalPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FiscalPeriodRepository extends JpaRepository<FiscalPeriod, Integer> {

    List<FiscalPeriod> findByFiscalYearIdOrderByPeriodNoAsc(Integer fiscalYearId);

    Optional<FiscalPeriod> findByFiscalYearIdAndPeriodNo(Integer fiscalYearId, Short periodNo);

    /**
     * The period a date belongs to, excluding the adjustment period.
     *
     * <p>Period 13 shares 31 December with period 12, so a plain date lookup
     * would find two rows for that one day. Adjustments are chosen deliberately,
     * never fallen into.
     */
    @Query("""
        SELECT p FROM FiscalPeriod p
        WHERE p.adjustment = false
          AND p.startDate <= :on
          AND p.endDate   >= :on
        """)
    Optional<FiscalPeriod> findCalendarPeriodFor(@Param("on") LocalDate on);
}
