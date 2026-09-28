package com.dilaw.mohe.accounting.repository;

import com.dilaw.mohe.accounting.model.WafarEarmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface WafarEarmarkRepository extends JpaRepository<WafarEarmark, Integer> {

    List<WafarEarmark> findByFiscalYearIdAndStudyCountryInId(Integer fiscalYearId, Integer studyCountryInId);

    List<WafarEarmark> findByStudentId(Integer studentId);

    @Query("""
        SELECT COALESCE(SUM(e.amount), 0) FROM WafarEarmark e
        WHERE e.fiscalYearId = :fiscalYearId
          AND e.studyCountryInId = :countryId
          AND e.currencyId = :currencyId
        """)
    BigDecimal sumEarmarked(@Param("fiscalYearId") Integer fiscalYearId,
                            @Param("countryId") Integer countryId,
                            @Param("currencyId") Integer currencyId);

    /**
     * What accounting earmarked per student and quarter: rows of
     * {@code [studentId, moheQuarterKey, sum(amount)]}. Earmarks with no student
     * (attaché-level ones) are left out; the Wafar reconciliation compares students.
     */
    @Query("""
        SELECT e.studentId, e.moheQuarterKey, SUM(e.amount) FROM WafarEarmark e
        WHERE e.studentId IS NOT NULL
        GROUP BY e.studentId, e.moheQuarterKey
        """)
    List<Object[]> sumByStudentAndQuarter();
}
