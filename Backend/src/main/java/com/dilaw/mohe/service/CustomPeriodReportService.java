package com.dilaw.mohe.service;

import com.dilaw.mohe.model.CustomPeriodReport;
import com.dilaw.mohe.repository.CustomPeriodReportRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class CustomPeriodReportService {

    @Autowired
    private CustomPeriodReportRepository customPeriodReportRepository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<CustomPeriodReport> getAllReports(Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return customPeriodReportRepository.findAllFiltered(
                applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry));
    }
}
