package com.dilaw.mohe.accounting.repository;

import com.dilaw.mohe.accounting.model.WafarUse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface WafarUseRepository extends JpaRepository<WafarUse, Integer> {

    List<WafarUse> findByFiscalYearIdAndStudyCountryInId(Integer fiscalYearId, Integer studyCountryInId);

    @Query("""
        SELECT COALESCE(SUM(u.amount), 0) FROM WafarUse u
        WHERE u.fiscalYearId = :fiscalYearId
          AND u.studyCountryInId = :countryId
          AND u.currencyId = :currencyId
        """)
    BigDecimal sumUsed(@Param("fiscalYearId") Integer fiscalYearId,
                       @Param("countryId") Integer countryId,
                       @Param("currencyId") Integer currencyId);
}
