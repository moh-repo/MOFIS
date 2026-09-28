package com.dilaw.mohe.service;

import com.dilaw.mohe.model.DistributionStatistics;
import com.dilaw.mohe.repository.DistributionStatisticsRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Transactional(readOnly = true)
@Service
public class DistributionStatisticsService {

    @Autowired
    private DistributionStatisticsRepository distributionStatisticsRepository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<DistributionStatistics> getAllDistributions(Integer countryId, List<Integer> requestCountryIds) {
        return search(countryId, requestCountryIds, null, null, null, null);
    }

    public List<DistributionStatistics> getDistributionsByType(Integer countryId, List<Integer> requestCountryIds, String distributionType) {
        return search(countryId, requestCountryIds, distributionType, null, null, null);
    }

    public List<DistributionStatistics> getDistributionsBySignificance(Integer countryId, String significance) {
        return search(countryId, null, null, significance, null, null);
    }

    public List<DistributionStatistics> getDistributionsByMinimumPercentage(Integer countryId, BigDecimal minPercentage) {
        return search(countryId, null, null, null, minPercentage, null);
    }

    public List<DistributionStatistics> getTopRankedDistributions(Integer countryId, Long maxRank) {
        return search(countryId, null, null, null, null, maxRank);
    }

    /**
     * Distribution types, derived from the rows the report actually returns.
     *
     * <p>This used to read {@code V_Distribution_Statistics}, the legacy view
     * the distributions no longer come from — they come from the native
     * four-way UNION in
     * {@link com.dilaw.mohe.repository.DistributionStatisticsRepository}. The
     * two had drifted, so the filter listed types that matched nothing.
     */
    public List<String> getAllDistributionTypes(Integer countryId) {
        return getAllDistributions(countryId, null).stream()
                .map(DistributionStatistics::getDistributionType)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private List<DistributionStatistics> search(Integer countryId,
                                                List<Integer> requestCountryIds,
                                                String distributionType,
                                                String significance,
                                                BigDecimal minPercentage,
                                                Long maxRank) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return distributionStatisticsRepository.search(
                applyCountry,
                countryScopeHelper.safeList(countryIds),
                distributionType,
                significance,
                minPercentage,
                maxRank);
    }
}
