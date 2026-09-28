package com.dilaw.mohe.service;

import com.dilaw.mohe.model.MonthlyReport;
import com.dilaw.mohe.repository.MonthlyReportRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class MonthlyReportService {

    @Autowired
    private MonthlyReportRepository monthlyReportRepository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    @Cacheable(value = "periodic-reports", key = "'monthly:all:' + #countryId + ':' + #requestCountryIds")
    public List<MonthlyReport> getAllReports(Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return monthlyReportRepository.findAllFiltered(
                applyCountry, countryScopeHelper.safeList(countryIds));
    }

    @Cacheable(value = "periodic-reports", key = "'monthly:year:' + #year + ':' + #countryId + ':' + #requestCountryIds")
    public List<MonthlyReport> getReportsByYear(Integer year, Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return monthlyReportRepository.findByYear(
                year, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<MonthlyReport> getReportsByYearRange(Integer startYear, Integer endYear, Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return monthlyReportRepository.findByYearRange(
                startYear, endYear, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public MonthlyReport getReportByYearAndMonth(Integer year, Integer month, Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return monthlyReportRepository.findByYearAndMonth(
                year, month, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<Integer> getAllYears(Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return monthlyReportRepository.findAllYears(
                applyCountry, countryScopeHelper.safeList(countryIds));
    }
}
