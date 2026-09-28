package com.dilaw.mohe.service;

import com.dilaw.mohe.model.BudgetAnalysis;
import com.dilaw.mohe.repository.BudgetAnalysisRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Transactional(readOnly = true)
@Service
public class BudgetAnalysisService {

    @Autowired
    private BudgetAnalysisRepository repository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<BudgetAnalysis> getAll(Integer countryId, List<Integer> requestCountryIds) {
        return getByFilters(countryId, requestCountryIds, null, null, null);
    }

    public List<BudgetAnalysis> getByFilters(Integer countryId, List<Integer> requestCountryIds, String category, Integer year, Integer quarter) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByFilters(
                applyCountry,
                countryScopeHelper.inClauseIds(countryIds, applyCountry),
                category,
                year,
                quarter);
    }

    public Map<String, Object> getMetadata(Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("categories", repository.findDistinctCategories(applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry)));
        metadata.put("years", repository.findDistinctYears(applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry)));
        metadata.put("quarters", repository.findDistinctQuarters(applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry), null));
        return metadata;
    }
}
