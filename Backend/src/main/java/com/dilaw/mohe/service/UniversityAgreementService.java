package com.dilaw.mohe.service;

import com.dilaw.mohe.model.UniversityAgreement;
import com.dilaw.mohe.repository.UniversityAgreementRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class UniversityAgreementService {

    @Autowired
    private UniversityAgreementRepository repository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<UniversityAgreement> getAllAgreements(Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findAllOrderedByStudentsCovered(
                applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<UniversityAgreement> getAgreementsByCountry(String countryName, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByCountry(
                countryName, applyCountry, countryScopeHelper.safeList(countryIds));
    }
}
