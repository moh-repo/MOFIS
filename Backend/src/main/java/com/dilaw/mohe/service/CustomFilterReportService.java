package com.dilaw.mohe.service;

import com.dilaw.mohe.model.CustomFilterReport;
import com.dilaw.mohe.repository.CustomFilterReportRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class CustomFilterReportService {

    @Autowired
    private CustomFilterReportRepository repository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<CustomFilterReport> getAllReports(Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findAllFiltered(applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry));
    }

    public List<CustomFilterReport> getReportsByStudyStatus(Integer statusId, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByStudyStatus(statusId, applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry));
    }

    public List<CustomFilterReport> getReportsByDegreeLevel(Integer degreeLevelId, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByDegreeLevel(degreeLevelId, applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry));
    }

    public List<CustomFilterReport> getReportsByStudyType(Integer typeId, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByStudyType(typeId, applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry));
    }

    public List<CustomFilterReport> getReportsByUniversityName(String universityName, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByUniversityName(universityName, applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry));
    }

    public List<CustomFilterReport> getReportsByEnrollmentYear(Integer year, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByEnrollmentYear(year, applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry));
    }
}
