package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentFinancialDataForm;
import com.dilaw.mohe.repository.StudentFinancialDataFormRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import com.dilaw.mohe.util.LocalizedLookupOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class StudentFinancialDataFormService {

    @Autowired
    private StudentFinancialDataFormRepository repository;

    @Autowired
    private CountryNameService countryNameService;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<StudentFinancialDataForm> getAll(Integer countryId) {
        ScopeContext ctx = resolveScope(countryId);
        return repository.findAllScoped(ctx.applyCountry, ctx.countryNames);
    }

    public List<StudentFinancialDataForm> getByQuarterAndYear(Integer quarter, Integer year, Integer countryId) {
        ScopeContext ctx = resolveScope(countryId);
        return repository.findByYearAndQuarterScoped(year, quarter, ctx.applyCountry, ctx.countryNames);
    }

    public List<StudentFinancialDataForm> getByCountryQuarterAndYear(
            String countryName, Integer quarter, Integer year, Integer countryId) {
        ScopeContext ctx = resolveScope(countryId);
        if (ctx.applyCountry && countryName != null && !ctx.countryNames.contains(countryName)) {
            return List.of();
        }
        return repository.findByCountryYearAndQuarterScoped(
                countryName, year, quarter, ctx.applyCountry, ctx.countryNames);
    }

    public Map<String, Object> getMetadata(Integer countryId) {
        ScopeContext ctx = resolveScope(countryId);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("years", repository.findDistinctYearsScoped(ctx.applyCountry, ctx.countryNames));
        // Named in the reader's language; the value stays the Arabic name the rows are filtered by (R5-29).
        metadata.put("countries", countryNameService.optionsByArabicName(
                repository.findDistinctCountriesScoped(ctx.applyCountry, ctx.countryNames),
                !LocalizedLookupOptions.english()));
        metadata.put("quarters", repository.findDistinctQuartersScoped(ctx.applyCountry, ctx.countryNames));
        return metadata;
    }

    public StudentFinancialDataForm getById(Integer id) {
        return repository.findById(id).orElse(null);
    }

    private ScopeContext resolveScope(Integer requestCountryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(requestCountryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);

        List<String> countryNames;
        if (!applyCountry) {
            countryNames = Collections.emptyList();
        } else {
            List<Integer> ids = countryScopeHelper.safeList(countryIds);
            countryNames = countryNameService.all().stream()
                    .filter(c -> ids.contains(c.id()))
                    .map(CountryNameService.CountryRef::nameAr)
                    .collect(Collectors.toList());
            if (countryNames.isEmpty()) {
                countryNames = List.of("__none__");
            }
        }
        return new ScopeContext(applyCountry, countryNames);
    }

    private record ScopeContext(boolean applyCountry, List<String> countryNames) {}
}
