package com.dilaw.mohe.ums.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

/**
 * Resolves effective study-country filters for report/analytics read APIs.
 * Uses STUDY_COUNTRY_IN (study destination), aligned with UMS UserCountries scope.
 */
@Component
public class ReportCountryScopeHelper {

    private final CountryScopeService countryScopeService;

    public ReportCountryScopeHelper(CountryScopeService countryScopeService) {
        this.countryScopeService = countryScopeService;
    }

    /**
     * @return null when no country filter should be applied (ministry, all countries);
     *         non-null list of STUDY_COUNTRY_IN ids otherwise.
     */
    public List<Integer> resolveCountryIds(Integer requestCountryId) {
        CountryScopeService.CountryScope scope = countryScopeService.getScope();
        if (requestCountryId != null) {
            countryScopeService.validateCountryAccess(requestCountryId);
            return List.of(requestCountryId);
        }
        if (scope.isMinistryUser()) {
            return null;
        }
        List<Integer> allowed = scope.getAllowedCountryIds();
        if (allowed.isEmpty()) {
            return List.of(-1);
        }
        return allowed;
    }

    /**
     * Multi-country overload: supports selecting a subset of allowed countries.
     * When countryIds is provided (non-empty), validates each id against user's scope
     * and uses the intersection. Falls back to single-id or full-scope resolution.
     *
     * @return null for ministry-all, non-null list otherwise.
     */
    public List<Integer> resolveCountryIds(Integer requestCountryId, List<Integer> requestCountryIds) {
        if (requestCountryIds != null && !requestCountryIds.isEmpty()) {
            CountryScopeService.CountryScope scope = countryScopeService.getScope();
            if (!scope.isMinistryUser()) {
                List<Integer> allowed = scope.getAllowedCountryIds();
                List<Integer> filtered = requestCountryIds.stream()
                        .filter(allowed::contains)
                        .distinct()
                        .collect(java.util.stream.Collectors.toList());
                return filtered.isEmpty() ? List.of(-1) : filtered;
            }
            // ministry user: accept any subset as-is
            return requestCountryIds.stream().distinct()
                    .collect(java.util.stream.Collectors.toList());
        }
        return resolveCountryIds(requestCountryId);
    }

    public boolean shouldApplyCountryFilter(List<Integer> countryIds) {
        return countryIds != null;
    }

    public void ensureScopeOrThrow() {
        CountryScopeService.CountryScope scope = countryScopeService.getScope();
        if (!scope.isMinistryUser() && scope.getAllowedCountryIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: no country scope");
        }
    }

    public List<Integer> emptyResultGuard(List<Integer> countryIds) {
        if (countryIds != null && countryIds.isEmpty()) {
            return List.of(-1);
        }
        return countryIds;
    }

    public List<Integer> safeList(List<Integer> countryIds) {
        return countryIds == null ? Collections.emptyList() : countryIds;
    }

    /**
     * Sentinel list for native/JPQL {@code IN (:countryIds)} when country filter is off.
     * Avoids Hibernate "Empty IN list" errors while keeping {@code applyCountryFilter = 0} branches effective.
     */
    public List<Integer> inClauseIds(List<Integer> countryIds, boolean applyCountry) {
        if (!applyCountry) {
            return List.of(-1);
        }
        List<Integer> safe = safeList(countryIds);
        return safe.isEmpty() ? List.of(-1) : safe;
    }
}
