package com.dilaw.mohe.service;

import com.dilaw.mohe.model.FeeStructure;
import com.dilaw.mohe.repository.FeeStructureRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class FeeStructureService {

    @Autowired
    private FeeStructureRepository repository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<FeeStructure> getAllFeeStructures(Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findAllOrderedByFeeAmount(
                applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<FeeStructure> getFeeStructuresByUniversity(Long universityId, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByUniversityId(
                universityId, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<FeeStructure> getFeeStructuresByDegreeLevel(Integer degreeLevelId, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByDegreeLevel(
                degreeLevelId, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<FeeStructure> getFeeStructuresByCountry(String countryName, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByCountry(
                countryName, applyCountry, countryScopeHelper.safeList(countryIds));
    }
}
