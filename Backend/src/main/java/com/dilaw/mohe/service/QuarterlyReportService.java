package com.dilaw.mohe.service;

import com.dilaw.mohe.model.QuarterlyReport;
import com.dilaw.mohe.repository.QuarterlyReportRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class QuarterlyReportService {

    @Autowired
    private QuarterlyReportRepository quarterlyReportRepository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<QuarterlyReport> getAllReports(Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return quarterlyReportRepository.findAllFiltered(
                applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<QuarterlyReport> getReportsByYear(Integer year, Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return quarterlyReportRepository.findByYear(
                year, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<QuarterlyReport> getReportsByYearRange(Integer startYear, Integer endYear, Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return quarterlyReportRepository.findByYearRange(
                startYear, endYear, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public QuarterlyReport getReportByYearAndQuarter(Integer year, Integer quarter, Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return quarterlyReportRepository.findByYearAndQuarter(
                year, quarter, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<Integer> getAllYears(Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return quarterlyReportRepository.findAllYears(
                applyCountry, countryScopeHelper.safeList(countryIds));
    }
}
