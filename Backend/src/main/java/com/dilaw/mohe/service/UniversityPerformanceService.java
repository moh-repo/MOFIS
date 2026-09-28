package com.dilaw.mohe.service;

import com.dilaw.mohe.model.UniversityPerformance;
import com.dilaw.mohe.repository.UniversityPerformanceRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Transactional(readOnly = true)
@Service
public class UniversityPerformanceService {

    @Autowired
    private UniversityPerformanceRepository repository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<UniversityPerformance> getAllPerformanceReports(Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findAllOrderedByEnrollment(
                applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<UniversityPerformance> getPerformanceByMinimumRetentionRate(BigDecimal minRate, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByMinimumRetentionRate(
                minRate, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<UniversityPerformance> getPerformanceByCountry(String countryName, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByCountry(
                countryName, applyCountry, countryScopeHelper.safeList(countryIds));
    }
}
