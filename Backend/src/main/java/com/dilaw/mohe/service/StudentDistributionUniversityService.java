package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDistributionUniversity;
import com.dilaw.mohe.repository.StudentDistributionUniversityRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class StudentDistributionUniversityService {

    @Autowired
    private StudentDistributionUniversityRepository repository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<StudentDistributionUniversity> getAllDistributions(Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findAllOrderedByStudentCount(
                applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<StudentDistributionUniversity> getDistributionsByCountry(String countryName, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByCountry(
                countryName, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<StudentDistributionUniversity> getDistributionsByMinimumStudents(Integer minStudents, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByMinimumActiveStudents(
                minStudents, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<StudentDistributionUniversity> getLargeUniversities(Integer threshold, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findLargeUniversities(
                threshold, applyCountry, countryScopeHelper.safeList(countryIds));
    }
}
