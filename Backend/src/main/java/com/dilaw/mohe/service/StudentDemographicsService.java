package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDemographics;
import com.dilaw.mohe.repository.StudentDemographicsRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class StudentDemographicsService {

    @Autowired
    private StudentDemographicsRepository repository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<StudentDemographics> search(Integer countryId,
                                            List<Integer> requestCountryIds,
                                            Integer genderId,
                                            String ageRange,
                                            Integer levelId,
                                            Integer studyTypeId,
                                            Integer degreeId,
                                            Integer majorId,
                                            Integer statusId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByFilters(
                applyCountry,
                countryScopeHelper.safeList(countryIds),
                genderId, ageRange, levelId, studyTypeId, degreeId, majorId, statusId);
    }
}
