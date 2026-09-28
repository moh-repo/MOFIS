package com.dilaw.mohe.service;

import com.dilaw.mohe.model.UniversityPartnership;
import com.dilaw.mohe.repository.UniversityPartnershipRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class UniversityPartnershipService {

    @Autowired
    private UniversityPartnershipRepository repository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<UniversityPartnership> getAllPartnerships(Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findAllOrderedByStudentCount(
                applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<UniversityPartnership> getPartnershipsByCountry(String countryName, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByCountry(
                countryName, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<UniversityPartnership> getPartnershipsByMinimumStudents(Integer minStudents, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByMinimumActiveStudents(
                minStudents, applyCountry, countryScopeHelper.safeList(countryIds));
    }

}
