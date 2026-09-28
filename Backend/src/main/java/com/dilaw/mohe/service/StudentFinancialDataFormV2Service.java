package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.ReportLabelCode;
import com.dilaw.mohe.model.StudentFinancialDataFormV2;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.repository.StudentFinancialDataFormV2Repository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import com.dilaw.mohe.util.LocalizedLookupOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Rows and metadata for the quarterly financial assistance statement
 * (Financial Data Form V2).
 *
 * <p>Country scope is applied by {@code STUDY_COUNTRY_IN_ID}, which the V2 view
 * exposes; the first form had to resolve country names because its view did
 * not carry the id.
 */
@Transactional(readOnly = true)
@Service
public class StudentFinancialDataFormV2Service {

    private final StudentFinancialDataFormV2Repository repository;
    private final ReportCountryScopeHelper countryScopeHelper;
    private final CodeListService codeLists;
    private final CountryNameService countryNameService;

    public StudentFinancialDataFormV2Service(StudentFinancialDataFormV2Repository repository,
                                             ReportCountryScopeHelper countryScopeHelper,
                                             CodeListService codeLists,
                                             CountryNameService countryNameService) {
        this.repository = repository;
        this.countryScopeHelper = countryScopeHelper;
        this.codeLists = codeLists;
        this.countryNameService = countryNameService;
    }

    public List<StudentFinancialDataFormV2> getByQuarterAndYear(Integer quarter, Integer year, Integer countryId) {
        Scope scope = resolveScope(countryId);
        return repository.findByYearAndQuarterScoped(year, quarter, scope.apply(), scope.ids());
    }

    public List<StudentFinancialDataFormV2> getByCountryQuarterAndYear(
            String countryName, Integer quarter, Integer year, Integer countryId) {
        Scope scope = resolveScope(countryId);
        return repository.findByCountryYearAndQuarterScoped(countryName, year, quarter, scope.apply(), scope.ids());
    }

    public Map<String, Object> getMetadata(Integer countryId) {
        Scope scope = resolveScope(countryId);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("years", repository.findDistinctYearsScoped(scope.apply(), scope.ids()));
        metadata.put("quarters", repository.findDistinctQuartersScoped(scope.apply(), scope.ids()));
        // Named in the reader's language; the value stays the Arabic name the rows are filtered by (R5-29).
        metadata.put("countries", countryNameService.optionsByArabicName(
                repository.findDistinctCountriesScoped(scope.apply(), scope.ids()),
                !LocalizedLookupOptions.english()));
        return metadata;
    }

    /** Sum of the quarterly allowances on the statement, as printed in its total row. */
    public static long totalAmount(List<StudentFinancialDataFormV2> rows) {
        return rows.stream()
                .map(StudentFinancialDataFormV2::getStudentAmount)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
    }

    /**
     * The single country of study the rows share, or {@code ""} when they span
     * more than one — the statement's head names the country only when there is
     * one to name.
     */
    public static String singleCountryName(List<StudentFinancialDataFormV2> rows) {
        List<String> names = rows.stream()
                .map(StudentFinancialDataFormV2::getStudyCountryInName)
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .toList();
        return names.size() == 1 ? names.get(0) : "";
    }

    /**
     * "الثالث" / "Third" for quarter 3, as the statement head writes it — the
     * ordinal words are {@code LU_REPORT_LABEL} rows (V076), not a switch here.
     */
    public String quarterLabel(Integer quarter, boolean rtl) {
        if (quarter == null) {
            return "";
        }
        String code = ReportLabelCode.quarterOrdinal(quarter);
        if (code == null) {
            return String.valueOf(quarter);
        }
        return codeLists.label("report-label", code, rtl ? "ar" : "en");
    }

    private Scope resolveScope(Integer requestCountryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(requestCountryId);
        boolean apply = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return new Scope(apply, countryScopeHelper.inClauseIds(countryIds, apply));
    }

    private record Scope(boolean apply, List<Integer> ids) {}
}
