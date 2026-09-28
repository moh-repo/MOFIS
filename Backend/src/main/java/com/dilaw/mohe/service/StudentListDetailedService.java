package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentListDetailed;
import com.dilaw.mohe.repository.StudentListDetailedRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.dilaw.mohe.util.LookupTablePageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class StudentListDetailedService {

    @Autowired
    private StudentListDetailedRepository repository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<StudentListDetailed> getAll() {
        countryScopeHelper.ensureScopeOrThrow();
        return getByFilters(null, null, null, null, null, null, null, null, null, null, null, 0, Integer.MAX_VALUE, "studentId", "asc")
                .getContent();
    }

    public Page<StudentListDetailed> getAllPaginated(int page, int size, String sortBy, String sortDir) {
        return getByFilters(null, null, null, null, null, null, null, null, null, null, null, page, size, sortBy, sortDir);
    }

    public Page<StudentListDetailed> getByFilters(
            Integer countryId,
            List<Integer> requestCountryIds,
            Integer majorId,
            Integer statusId,
            Integer genderId,
            Integer degreeId,
            Integer levelId,
            Integer studyTypeId,
            Integer universityTypeId,
            Integer agencyTypeId,
            Integer ministryTypeId,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        java.util.Set<String> allowed = java.util.Set.of(
                "studentId", "studentName", "studentPassportNumber", "studentCountryName",
                "studentMajorName", "studentDegreeName", "studentGender");
        Sort sort = LookupTablePageUtil.toSort(sortBy, sortDir, allowed, "studentId");
        Pageable pageable = PageRequest.of(page, size, sort);
        return repository.findByFilters(
                applyCountry,
                countryScopeHelper.safeList(countryIds),
                majorId,
                statusId,
                genderId,
                degreeId,
                levelId,
                studyTypeId,
                universityTypeId,
                agencyTypeId,
                ministryTypeId,
                pageable);
    }

    public Page<StudentListDetailed> searchStudents(String search, Integer countryId, List<Integer> requestCountryIds, int page, int size) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        Pageable pageable = PageRequest.of(page, size);
        return repository.searchStudents(
                search,
                applyCountry,
                countryScopeHelper.safeList(countryIds),
                pageable);
    }

    public StudentListDetailed getById(Integer id) {
        return repository.findById(id).orElse(null);
    }
}
