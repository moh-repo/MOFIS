package com.dilaw.mohe.service;

import com.dilaw.mohe.model.PredictiveAnalytics;
import com.dilaw.mohe.repository.PredictiveAnalyticsRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class PredictiveAnalyticsService {

    @Autowired
    private PredictiveAnalyticsRepository predictiveAnalyticsRepository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<PredictiveAnalytics> getAllForecasts(Integer countryId, List<Integer> requestCountryIds) {
        return search(countryId, requestCountryIds, null, null, null, null);
    }

    public List<PredictiveAnalytics> getForecastsByCategory(Integer countryId, String category) {
        return search(countryId, null, category, null, null, null);
    }

    public List<PredictiveAnalytics> getForecastsByYear(Integer countryId, Integer year) {
        return search(countryId, null, null, year, null, null);
    }

    public List<PredictiveAnalytics> getForecastsByConfidenceLevel(Integer countryId, String level) {
        return search(countryId, null, null, null, level, null);
    }

    public List<PredictiveAnalytics> getForecastsByTrend(Integer countryId, String trend) {
        return search(countryId, null, null, null, null, trend);
    }

    /**
     * Forecast categories, derived from the forecasts the report actually
     * returns.
     *
     * <p>This used to read the legacy predictive-analytics view (dropped by V066),
     * which the forecasts no longer come from — they come from the native linear
     * extrapolation in
     * {@link com.dilaw.mohe.repository.PredictiveAnalyticsRepository}. The two
     * had drifted, so the filter offered categories that never matched a row.
     */
    public List<String> getAllForecastCategories(Integer countryId) {
        return getAllForecasts(countryId, null).stream()
                .map(PredictiveAnalytics::getForecastCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private List<PredictiveAnalytics> search(Integer countryId,
                                             List<Integer> requestCountryIds,
                                             String category,
                                             Integer year,
                                             String level,
                                             String trend) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return predictiveAnalyticsRepository.search(
                applyCountry,
                countryScopeHelper.inClauseIds(countryIds, applyCountry),
                category,
                year,
                level,
                trend);
    }
}
