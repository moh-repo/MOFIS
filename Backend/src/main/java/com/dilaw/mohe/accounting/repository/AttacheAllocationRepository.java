package com.dilaw.mohe.accounting.repository;

import com.dilaw.mohe.accounting.model.AttacheAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Repository
public interface AttacheAllocationRepository extends JpaRepository<AttacheAllocation, Integer> {

    List<AttacheAllocation> findByMoheQuarterKeyOrderByStudyCountryInIdAsc(Integer moheQuarterKey);

    List<AttacheAllocation> findByFiscalYearIdAndStudyCountryInId(Integer fiscalYearId, Integer studyCountryInId);

    /**
     * What has actually reached an attaché. Only TRANSFERRED counts: a draft or
     * merely approved allocation is money the attaché cannot spend yet.
     */
    @Query("""
        SELECT COALESCE(SUM(a.amount), 0) FROM AttacheAllocation a
        WHERE a.fiscalYearId = :fiscalYearId
          AND a.studyCountryInId = :countryId
          AND a.currencyId = :currencyId
          AND a.status = :#{T(com.dilaw.mohe.codes.AllocationStatus).TRANSFERRED}
        """)
    BigDecimal sumTransferred(@Param("fiscalYearId") Integer fiscalYearId,
                              @Param("countryId") Integer countryId,
                              @Param("currencyId") Integer currencyId);

    /** The quarterly roll-up MOHE_QUARTER has never held. */
    @Query("""
        SELECT COALESCE(SUM(a.amount), 0) FROM AttacheAllocation a
        WHERE a.moheQuarterKey = :quarterKey
          AND a.status = :#{T(com.dilaw.mohe.codes.AllocationStatus).TRANSFERRED}
        """)
    BigDecimal sumTransferredForQuarter(@Param("quarterKey") Integer quarterKey);

    /**
     * FIN-08: the quarter's transfers per currency, rows {@code [currencyId, amount]},
     * for the quarter-close budget check. The single-figure roll-up above adds the
     * currencies together and cannot be compared against anything.
     */
    @Query("""
        SELECT a.currencyId, COALESCE(SUM(a.amount), 0) FROM AttacheAllocation a
        WHERE a.moheQuarterKey = :quarterKey
          AND a.status = :#{T(com.dilaw.mohe.codes.AllocationStatus).TRANSFERRED}
        GROUP BY a.currencyId
        """)
    List<Object[]> sumTransferredForQuarterByCurrency(@Param("quarterKey") Integer quarterKey);

    /**
     * Transferred allocations per quarter, for the MOHE Quarters screen.
     *
     * <p>Rows are {@code [moheQuarterKey, amount]}, one query for the whole list.
     *
     * <p>The stored {@code MOHE_QUARTER.BUDGET_AMOUNT} that
     * {@code AllocationService.rollUpToQuarter} maintains is the <em>ministry's</em>
     * figure and stays that way — it is a real total and scoping the write would
     * corrupt it. The screen instead reads this, so a country officer is shown what
     * reached their own attachés rather than what reached every attaché.
     *
     * @param unscoped   true for a ministry user; {@code countryIds} is then ignored
     * @param countryIds never empty — SQL Server rejects {@code IN ()}
     */
    @Query("""
        SELECT a.moheQuarterKey, COALESCE(SUM(a.amount), 0) FROM AttacheAllocation a
        WHERE a.status = :#{T(com.dilaw.mohe.codes.AllocationStatus).TRANSFERRED}
          AND a.moheQuarterKey IS NOT NULL
          AND (:unscoped = TRUE OR a.studyCountryInId IN :countryIds)
        GROUP BY a.moheQuarterKey
        """)
    List<Object[]> sumTransferredByQuarter(@Param("unscoped") boolean unscoped,
                                           @Param("countryIds") Collection<Integer> countryIds);

    /**
     * What each appropriation has been drawn down by, for the budget screen.
     *
     * <p>Rows are {@code [feeTypeId, currencyId, studyCountryInId, status, amount]}
     * for every allocation of the year that is approved or transferred — a draft
     * commits nothing and a cancelled one never happened. Allocations with no
     * fee type cover every stream at once and cannot be attributed to one
     * appropriation; they come back with a null first element and the service
     * leaves them out of the per-stream figures.
     */
    @Query("""
        SELECT a.feeTypeId, a.currencyId, a.studyCountryInId, a.status, COALESCE(SUM(a.amount), 0)
        FROM AttacheAllocation a
        WHERE a.fiscalYearId = :fiscalYearId
          AND a.status IN (:#{T(com.dilaw.mohe.codes.AllocationStatus).APPROVED}, :#{T(com.dilaw.mohe.codes.AllocationStatus).TRANSFERRED})
        GROUP BY a.feeTypeId, a.currencyId, a.studyCountryInId, a.status
        """)
    List<Object[]> sumCommittedByStream(@Param("fiscalYearId") Integer fiscalYearId);
}
