package com.dilaw.mohe.service;

import com.dilaw.mohe.model.PerformanceMetrics;
import com.dilaw.mohe.repository.PerformanceMetricsRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Transactional(readOnly = true)
@Service
public class PerformanceMetricsService {

    @Autowired
    private PerformanceMetricsRepository performanceMetricsRepository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<PerformanceMetrics> getAllMetrics(Integer countryId, List<Integer> requestCountryIds) {
        return search(countryId, requestCountryIds, null, null, null, null);
    }

    public List<PerformanceMetrics> getMetricsByCategory(Integer countryId, String category) {
        return search(countryId, null, category, null, null, null);
    }

    public List<PerformanceMetrics> getMetricsByPerformanceStatus(Integer countryId, String status) {
        return search(countryId, null, null, status, null, null);
    }

    public List<PerformanceMetrics> getMetricsByMinimumAchievement(Integer countryId, BigDecimal minPercentage) {
        return search(countryId, null, null, null, null, minPercentage);
    }

    public List<PerformanceMetrics> getMetricsByMeasurementPeriod(Integer countryId, String period) {
        return search(countryId, null, null, null, period, null);
    }

    /**
     * Metric categories, derived from the metrics themselves.
     *
     * <p>This used to read {@code V_Performance_Metrics}, the legacy view the
     * metrics no longer come from. The two sources had drifted apart, so the
     * filter offered categories that the current query never emits — selecting
     * one returned an empty report. Deriving from the same rows the page shows
     * keeps the filter and the data in step by construction, exactly as
     * {@link #getAllPerformanceStatuses} already did.
     */
    public List<String> getAllMetricCategories(Integer countryId) {
        return getAllMetrics(countryId, null).stream()
                .map(PerformanceMetrics::getMetricCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public List<String> getAllPerformanceStatuses(Integer countryId) {
        return getAllMetrics(countryId, null).stream()
                .map(PerformanceMetrics::getPerformanceStatus)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public List<PerformanceMetrics> getFiltered(Integer countryId, List<Integer> requestCountryIds, String category, String status) {
        return search(countryId, requestCountryIds, category, status, null, null);
    }

    private List<PerformanceMetrics> search(Integer countryId,
                                            List<Integer> requestCountryIds,
                                            String category,
                                            String status,
                                            String period,
                                            BigDecimal minPercentage) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return performanceMetricsRepository.search(
                applyCountry,
                countryScopeHelper.safeList(countryIds),
                category,
                status,
                period,
                minPercentage);
    }
}
