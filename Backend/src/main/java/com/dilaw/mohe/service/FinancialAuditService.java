package com.dilaw.mohe.service;

import com.dilaw.mohe.model.FinancialAudit;
import com.dilaw.mohe.repository.FinancialAuditRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Transactional(readOnly = true)
@Service
public class FinancialAuditService {

    private final FinancialAuditRepository repository;
    private final ReportCountryScopeHelper countryScopeHelper;

    public FinancialAuditService(FinancialAuditRepository repository,
                                 ReportCountryScopeHelper countryScopeHelper) {
        this.repository = repository;
        this.countryScopeHelper = countryScopeHelper;
    }

    public List<FinancialAudit> getAll(Integer countryId) {
        return getByFilters(countryId, null, null, null, null, null, null, null, null, null, null, null,
                Pageable.unpaged()).getContent();
    }

    public Page<FinancialAudit> getByFilters(
            Integer countryId,
            List<Integer> requestCountryIds,
            String status,
            String category,
            Integer year,
            Integer quarter,
            String bank,
            String auditor,
            String currency,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            Pageable pageable) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByFilters(status, category, year, quarter, bank, auditor, currency,
                startDate, endDate, applyCountry, countryScopeHelper.safeList(countryIds), search, pageable);
    }

    public List<FinancialAudit> getByStatus(Integer countryId, String status) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByAuditStatus(status, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<FinancialAudit> getByCategory(Integer countryId, String category) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByAuditCategory(category, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<FinancialAudit> getByYear(Integer countryId, Integer year) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByFiscalYear(year, applyCountry, countryScopeHelper.safeList(countryIds));
    }
}
