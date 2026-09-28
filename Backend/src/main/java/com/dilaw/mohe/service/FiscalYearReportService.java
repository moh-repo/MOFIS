package com.dilaw.mohe.service;

import com.dilaw.mohe.model.FiscalYearReport;
import com.dilaw.mohe.repository.FiscalYearReportRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Transactional(readOnly = true)
@Service
public class FiscalYearReportService {

    @Autowired
    private FiscalYearReportRepository fiscalYearReportRepository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<FiscalYearReport> getAllReports(Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return fiscalYearReportRepository.findAllOrdered(
                applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry));
    }

    public List<FiscalYearReport> getReportsByYearRange(Integer startYear, Integer endYear, Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return fiscalYearReportRepository.findByYearRange(
                startYear, endYear, applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry));
    }

    public FiscalYearReport getReportByYear(Integer year, Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return fiscalYearReportRepository.findByYear(
                year, applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry));
    }

    public List<FiscalYearReport> getReportsByMinimumUtilizationRate(BigDecimal minRate, Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return fiscalYearReportRepository.findByMinimumUtilizationRate(
                minRate, applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry));
    }
}
