package com.dilaw.mohe.service;

import com.dilaw.mohe.model.AcademicPerformance;
import com.dilaw.mohe.repository.AcademicPerformanceRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class AcademicPerformanceService {

    @Autowired
    private AcademicPerformanceRepository repository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<AcademicPerformance> getAll() {
        return getByFilters(null, null, null, null, null, null);
    }

    public List<AcademicPerformance> getByCountry(Integer countryId) {
        return getByFilters(countryId, null, null, null, null, null);
    }

    public List<AcademicPerformance> getByMajor(Integer majorId) {
        return getByFilters(null, null, majorId, null, null, null);
    }

    public List<AcademicPerformance> getByFilters(
            Integer countryId,
            List<Integer> requestCountryIds,
            Integer majorId,
            Integer statusId,
            Integer degreeId,
            Integer levelId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByFilters(
                applyCountry,
                countryScopeHelper.safeList(countryIds),
                majorId, statusId, degreeId, levelId);
    }

    public AcademicPerformance getById(Integer id) {
        return repository.findById(id).orElse(null);
    }
}
