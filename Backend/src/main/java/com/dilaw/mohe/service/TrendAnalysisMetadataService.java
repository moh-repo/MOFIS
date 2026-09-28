package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.dto.TrendAnalysisMetadataDto;
import com.dilaw.mohe.model.TrendAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Filter options for the trend analysis page, derived from the rows the page
 * will actually show.
 *
 * <p>They used to be read from {@code V_Trend_Analysis} while the rows came from
 * a separate query in the repository, and the two disagreed. The view periods by
 * the record's last-modified timestamp and sums the gross amount; the query
 * periods by the payment month and sums the net. So the year dropdown offered
 * years no row could match, and it ignored country scope entirely because the
 * view carries no country column.
 */
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class TrendAnalysisMetadataService {

    private final TrendAnalysisService trendAnalysisService;

    public TrendAnalysisMetadataDto getMetadata(Integer countryId, List<Integer> requestCountryIds) {
        List<TrendAnalysis> rows = trendAnalysisService.getAll(countryId, requestCountryIds);

        return TrendAnalysisMetadataDto.builder()
                .metricTypes(options(rows.stream().map(TrendAnalysis::getMetricType)))
                .directions(options(rows.stream().map(TrendAnalysis::getTrendDirection)))
                .years(rows.stream()
                        .map(TrendAnalysis::getTrendYear)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted(Comparator.reverseOrder())
                        .collect(Collectors.toList()))
                .build();
    }

    private static List<LookupOptionDto> options(Stream<String> values) {
        return values
                .filter(StringUtils::hasText)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(name -> new LookupOptionDto(null, name))
                .collect(Collectors.toList());
    }
}
