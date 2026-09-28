package com.dilaw.mohe.accounting.repository;

import com.dilaw.mohe.accounting.model.AttacheAdvanceBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttacheAdvanceBalanceRepository extends JpaRepository<AttacheAdvanceBalance, Integer> {

    /** The one balance for an attaché, year and currency — unique by constraint. */
    Optional<AttacheAdvanceBalance> findByFiscalYearIdAndStudyCountryInIdAndCurrencyId(
            Integer fiscalYearId, Integer studyCountryInId, Integer currencyId);

    List<AttacheAdvanceBalance> findByFiscalYearId(Integer fiscalYearId);

    List<AttacheAdvanceBalance> findByStudyCountryInId(Integer studyCountryInId);
}
