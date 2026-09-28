package com.dilaw.mohe.service;

import com.dilaw.mohe.model.DataVisualization;
import com.dilaw.mohe.repository.DataVisualizationRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The data-visualization report, narrowed to the caller's study countries in the
 * application (RPT-05) as every other report is — row-level security is the net
 * under that, not the only thing between a scoped user and another country's counts.
 */
@Transactional(readOnly = true)
@Service
public class DataVisualizationService {

    @Autowired
    private DataVisualizationRepository repository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    /** The caller's scope, resolved once per call: whether to filter, and on which ids. */
    private record Scope(boolean apply, List<Integer> ids) {
    }

    private Scope scope(Integer countryId, List<Integer> countryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> resolved = countryScopeHelper.resolveCountryIds(countryId, countryIds);
        boolean apply = countryScopeHelper.shouldApplyCountryFilter(resolved);
        return new Scope(apply, countryScopeHelper.inClauseIds(resolved, apply));
    }

    public List<DataVisualization> getAllVisualizations() {
        return getAllVisualizations(null, null);
    }

    public List<DataVisualization> getAllVisualizations(Integer countryId, List<Integer> countryIds) {
        Scope s = scope(countryId, countryIds);
        return repository.findAllOrderedBySortOrder(s.apply(), s.ids());
    }

    public List<DataVisualization> getVisualizationsByChartType(String chartType) {
        return getVisualizationsByChartType(chartType, null, null);
    }

    public List<DataVisualization> getVisualizationsByChartType(String chartType, Integer countryId, List<Integer> countryIds) {
        Scope s = scope(countryId, countryIds);
        return repository.findByChartType(chartType, s.apply(), s.ids());
    }

    public List<DataVisualization> getVisualizationsByChartName(String chartName) {
        return getVisualizationsByChartName(chartName, null, null);
    }

    public List<DataVisualization> getVisualizationsByChartName(String chartName, Integer countryId, List<Integer> countryIds) {
        Scope s = scope(countryId, countryIds);
        return repository.findByChartName(chartName, s.apply(), s.ids());
    }

    public List<DataVisualization> getVisualizationsByCategory(String category) {
        return getVisualizationsByCategory(category, null, null);
    }

    public List<DataVisualization> getVisualizationsByCategory(String category, Integer countryId, List<Integer> countryIds) {
        Scope s = scope(countryId, countryIds);
        return repository.findByCategory(category, s.apply(), s.ids());
    }

    public List<String> getAllChartNames() {
        return getAllChartNames(null, null);
    }

    public List<String> getAllChartNames(Integer countryId, List<Integer> countryIds) {
        Scope s = scope(countryId, countryIds);
        return repository.findAllChartNames(s.apply(), s.ids());
    }
}
