package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.ComparativeStatisticsMetadataDto;
import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.model.ComparativeStatistics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Filter options for the comparative statistics page.
 *
 * <p>The period lists are the cohort years the data actually holds. They used to
 * be read from {@code V_Comparative_Statistics}, whose two period columns are
 * {@code YEAR(GETDATE())} and {@code YEAR(GETDATE()) - 1} — so each dropdown
 * offered exactly one clock-derived value that nothing in the database could
 * influence. The comparison types now likewise come from the rows the page will
 * show, and both lists honour country scope, which the view cannot express
 * because it carries no country column.
 */
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class ComparativeStatisticsMetadataService {

    private final ComparativeStatisticsService statisticsService;

    public ComparativeStatisticsMetadataDto getMetadata(Integer countryId, List<Integer> requestCountryIds) {
        List<String> periods = statisticsService.availableYears(countryId, requestCountryIds).stream()
                .map(String::valueOf)
                .collect(Collectors.toList());

        List<ComparativeStatistics> rows = statisticsService.getAll(countryId, requestCountryIds);
        List<LookupOptionDto> comparisonTypes = rows.stream()
                .map(ComparativeStatistics::getComparisonType)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(name -> new LookupOptionDto(null, name))
                .collect(Collectors.toList());

        return ComparativeStatisticsMetadataDto.builder()
                .comparisonTypes(comparisonTypes)
                // Either end of the comparison may be any cohort year, so both
                // dropdowns offer the same list.
                .currentPeriods(periods)
                .previousPeriods(periods)
                .build();
    }
}
