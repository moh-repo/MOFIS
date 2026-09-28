package com.dilaw.mohe.accounting.repository;

import com.dilaw.mohe.accounting.model.BudgetAppropriation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetAppropriationRepository extends JpaRepository<BudgetAppropriation, Integer> {

    List<BudgetAppropriation> findByFiscalYearIdOrderByFeeTypeIdAscCurrencyIdAscStudyCountryInIdAsc(
            Integer fiscalYearId);
}
