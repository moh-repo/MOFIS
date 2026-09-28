package com.dilaw.mohe.service;

import com.dilaw.mohe.model.AnnualReport;
import com.dilaw.mohe.repository.AnnualReportRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class AnnualReportService {

    @Autowired
    private AnnualReportRepository annualReportRepository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    @Cacheable(value = "periodic-reports", key = "'annual:all:' + #countryId + ':' + #requestCountryIds")
    public List<AnnualReport> getAllReports(Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return annualReportRepository.findAllOrdered(
                applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<AnnualReport> getReportsByYearRange(Integer startYear, Integer endYear, Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return annualReportRepository.findByYearRange(
                startYear, endYear, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    @Cacheable(value = "periodic-reports", key = "'annual:year:' + #year + ':' + #countryId + ':' + #requestCountryIds")
    public AnnualReport getReportByYear(Integer year, Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return annualReportRepository.findByYear(
                year, applyCountry, countryScopeHelper.safeList(countryIds));
    }
}
