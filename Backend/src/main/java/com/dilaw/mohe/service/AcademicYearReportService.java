package com.dilaw.mohe.service;

import com.dilaw.mohe.model.AcademicYearReport;
import com.dilaw.mohe.repository.AcademicYearReportRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Transactional(readOnly = true)
@Service
public class AcademicYearReportService {

    @Autowired
    private AcademicYearReportRepository academicYearReportRepository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<AcademicYearReport> getAllReports(Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return academicYearReportRepository.findAllOrdered(
                applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry));
    }

    public AcademicYearReport getReportByAcademicYear(String year, Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return academicYearReportRepository.findByAcademicYear(
                year, applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry));
    }

    public List<AcademicYearReport> getReportsByMinimumGraduationRate(BigDecimal minRate, Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return academicYearReportRepository.findByMinimumGraduationRate(
                minRate, applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry));
    }
}
