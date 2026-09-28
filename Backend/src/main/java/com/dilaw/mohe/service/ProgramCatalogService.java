package com.dilaw.mohe.service;

import com.dilaw.mohe.model.ProgramCatalog;
import com.dilaw.mohe.repository.ProgramCatalogRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class ProgramCatalogService {

    @Autowired
    private ProgramCatalogRepository repository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<ProgramCatalog> getAllPrograms(Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findAllOrderedByEnrollment(
                applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<ProgramCatalog> getProgramsByUniversity(Long universityId, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByUniversityId(
                universityId, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<ProgramCatalog> getProgramsByDegreeLevel(Integer degreeLevelId, Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByDegreeLevel(
                degreeLevelId, applyCountry, countryScopeHelper.safeList(countryIds));
    }
}
