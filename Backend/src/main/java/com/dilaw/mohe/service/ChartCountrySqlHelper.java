package com.dilaw.mohe.service;

import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds SQL fragments for chart queries scoped by STUDY_COUNTRY_IN.
 */
@Component
public class ChartCountrySqlHelper {

    private final ReportCountryScopeHelper countryScopeHelper;

    public ChartCountrySqlHelper(ReportCountryScopeHelper countryScopeHelper) {
        this.countryScopeHelper = countryScopeHelper;
    }

    public record ScopeContext(List<Integer> countryIds, boolean applyFilter) {}

    public ScopeContext resolveScope(Integer requestCountryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(requestCountryId);
        boolean apply = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return new ScopeContext(countryScopeHelper.safeList(countryIds), apply);
    }

    /** AND sd.STUDY_COUNTRY_IN IN (1,2,3) — empty string when no filter. */
    public String studentDataInClause(String sdAlias, ScopeContext scope) {
        if (!scope.applyFilter() || scope.countryIds().isEmpty()) {
            return "";
        }
        return " AND " + sdAlias + ".STUDY_COUNTRY_IN IN (" + joinIds(scope.countryIds()) + ")";
    }

    /** AND sf.STUDY_COUNTRY_IN_ID IN (...) when finance row has study country. */
    public String financeDirectInClause(String sfAlias, ScopeContext scope) {
        if (!scope.applyFilter() || scope.countryIds().isEmpty()) {
            return "";
        }
        return " AND " + sfAlias + ".STUDY_COUNTRY_IN_ID IN (" + joinIds(scope.countryIds()) + ")";
    }

    /** EXISTS subquery linking finance to student study country. */
    public String financeViaStudentExists(String sfAlias, ScopeContext scope) {
        if (!scope.applyFilter() || scope.countryIds().isEmpty()) {
            return "";
        }
        return " AND EXISTS (SELECT 1 FROM MOHECORE.STUDENT_DATA sd "
                + "WHERE sd.STUDENT_ID = " + sfAlias + ".STUDENT_ID "
                + "AND ISNULL(sd.IS_DELETED, 0) = 0 "
                + "AND sd.STUDY_COUNTRY_IN IN (" + joinIds(scope.countryIds()) + "))";
    }

    /** Filter view rows by StudyCountryInId or CountryId column when present. */
    public String viewCountryInClause(String columnName, ScopeContext scope) {
        if (!scope.applyFilter() || scope.countryIds().isEmpty()) {
            return "";
        }
        return " WHERE " + columnName + " IN (" + joinIds(scope.countryIds()) + ")";
    }

    public String andViewCountryInClause(String columnName, ScopeContext scope) {
        if (!scope.applyFilter() || scope.countryIds().isEmpty()) {
            return "";
        }
        return " AND " + columnName + " IN (" + joinIds(scope.countryIds()) + ")";
    }

    private static String joinIds(List<Integer> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
