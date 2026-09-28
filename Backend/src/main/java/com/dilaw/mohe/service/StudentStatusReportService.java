package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.dto.StudentStatusMetadataDto;
import com.dilaw.mohe.model.StudentMajor;
import com.dilaw.mohe.model.StudentStatusReport;
import com.dilaw.mohe.model.StudentStudyStatus;
import com.dilaw.mohe.repository.StudentMajorRepository;
import com.dilaw.mohe.repository.StudentStatusReportRepository;
import com.dilaw.mohe.repository.StudentStudyStatusRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import com.dilaw.mohe.util.LocalizedLookupOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class StudentStatusReportService {

    @Autowired
    private StudentStatusReportRepository repository;

    @Autowired
    private StudentStudyStatusRepository studyStatusRepository;

    @Autowired
    private CountryNameService countryNameService;

    @Autowired
    private StudentMajorRepository majorRepository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    @Autowired
    private CountryScopeService countryScopeService;

    public List<StudentStatusReport> search(Integer statusId, Integer countryId, List<Integer> requestCountryIds, Integer majorId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.search(
                statusId,
                applyCountry,
                countryScopeHelper.safeList(countryIds),
                majorId);
    }

    public List<StudentStatusReport> getByStatusCategory(String category) {
        return search(null, null, null, null).stream()
                .filter(r -> category.equals(r.getStatusCategory()))
                .toList();
    }

    public List<StudentStatusReport> getByCountry(Integer countryId) {
        return search(null, countryId, null, null);
    }

    public List<String> getDistinctStatusCategories(Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, null);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findDistinctStatusCategories(
                applyCountry,
                countryScopeHelper.safeList(countryIds));
    }

    public StudentStatusMetadataDto getMetadata() {
        List<LookupOptionDto> statuses = LocalizedLookupOptions.of(studyStatusRepository.findAll(), StudentStudyStatus::getId);

        CountryScopeService.CountryScope scope = countryScopeService.getScope();
        List<LookupOptionDto> countries = countryNameService.all()
                .stream()
                .filter(c -> scope.isMinistryUser() || scope.getAllowedCountryIds().contains(c.id()))
                .map(this::mapCountry)
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .collect(Collectors.toList());

        List<LookupOptionDto> majors = LocalizedLookupOptions.of(majorRepository.findAll(), StudentMajor::getId);

        return StudentStatusMetadataDto.builder()
                .statuses(statuses)
                .countries(countries)
                .majors(majors)
                .build();
    }

    private LookupOptionDto mapCountry(CountryNameService.CountryRef country) {
        // name(...) rather than a raw name: the comparator above would NPE on a null name.
        return new LookupOptionDto(country.id(), country.name(!LocalizedLookupOptions.english()));
    }
}
