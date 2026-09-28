package com.dilaw.mohe.service;

import com.dilaw.mohe.model.TrendAnalysis;
import com.dilaw.mohe.repository.TrendAnalysisRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class TrendAnalysisService {

    private final TrendAnalysisRepository repository;
    private final ReportCountryScopeHelper countryScopeHelper;

    public List<TrendAnalysis> search(String metricType,
                                      String direction,
                                      Integer startYear,
                                      Integer endYear,
                                      Integer countryId,
                                      List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);

        String normalizedMetricType = normalize(metricType);
        String normalizedDirection = normalize(direction);
        Integer effectiveStartYear = startYear;
        Integer effectiveEndYear = endYear;

        if (effectiveStartYear != null && effectiveEndYear != null && effectiveStartYear > effectiveEndYear) {
            int temp = effectiveStartYear;
            effectiveStartYear = effectiveEndYear;
            effectiveEndYear = temp;
        }

        return repository.findByFilters(
                normalizedMetricType,
                normalizedDirection,
                effectiveStartYear,
                effectiveEndYear,
                applyCountry,
                countryScopeHelper.safeList(countryIds));
    }

    public List<TrendAnalysis> getAll(Integer countryId, List<Integer> requestCountryIds) {
        return search(null, null, null, null, countryId, requestCountryIds);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
