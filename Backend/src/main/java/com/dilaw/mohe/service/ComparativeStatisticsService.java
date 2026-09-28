package com.dilaw.mohe.service;

import com.dilaw.mohe.model.ComparativeStatistics;
import com.dilaw.mohe.repository.ComparativeStatisticsRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * Compares student counts per dimension between two cohort years.
 *
 * <p>The years come from the data. Previously both periods were derived from
 * {@code GETDATE()} and the counts were taken from the students' year of birth,
 * so the page compared "students born this year" against "students born last
 * year" — zero against zero, for every dimension.
 */
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class ComparativeStatisticsService {

    private final ComparativeStatisticsRepository repository;
    private final ReportCountryScopeHelper countryScopeHelper;

    /** The two years a comparison covers, both known to exist in the data. */
    public record Period(Integer current, Integer previous) {}

    public List<ComparativeStatistics> search(String comparisonType,
                                              String currentPeriod,
                                              String previousPeriod,
                                              Integer countryId,
                                              List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        List<Integer> safeIds = countryScopeHelper.safeList(countryIds);

        Period period = resolvePeriod(currentPeriod, previousPeriod, applyCountry, safeIds);
        if (period.current() == null) {
            return Collections.emptyList();
        }

        return repository.findComparison(
                normalize(comparisonType),
                period.current(),
                period.previous(),
                applyCountry,
                safeIds);
    }

    public List<ComparativeStatistics> getAll(Integer countryId, List<Integer> requestCountryIds) {
        return search(null, null, null, countryId, requestCountryIds);
    }

    /** Cohort years present in the data, most recent first. */
    public List<Integer> availableYears(Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findDistinctDecisionYears(applyCountry, countryScopeHelper.safeList(countryIds));
    }

    /**
     * Resolves the pair to compare. An unspecified current period defaults to the
     * most recent cohort year that is not in the future; an unspecified previous
     * period defaults to the next year down that also has students, so the
     * default compares two populated years rather than one against an empty one.
     *
     * <p>Future years are skipped only when choosing the default. This database
     * holds decision years as far out as 2042, one student apiece, which look
     * like data entry errors — defaulting to them would open the page on a
     * comparison of one student against one. They remain selectable, because
     * they are in the data.
     */
    private Period resolvePeriod(String requestedCurrent, String requestedPrevious,
                                 boolean applyCountry, List<Integer> countryIds) {
        List<Integer> years = repository.findDistinctDecisionYears(applyCountry, countryIds);
        if (years.isEmpty()) {
            return new Period(null, null);
        }

        Integer current = parseYear(requestedCurrent);
        if (current == null) {
            int thisYear = java.time.Year.now().getValue();
            current = years.stream()
                    .filter(y -> y <= thisYear)
                    .findFirst()
                    .orElse(years.get(0));
        }

        Integer previous = parseYear(requestedPrevious);
        if (previous == null) {
            final int currentYear = current;
            previous = years.stream().filter(y -> y < currentYear).findFirst().orElse(currentYear - 1);
        }
        return new Period(current, previous);
    }

    private static Integer parseYear(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            // A period that is not a year cannot select anything; fall back to
            // the default rather than returning an empty page with no reason.
            return null;
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
