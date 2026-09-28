package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.AcademicPerformanceMetadataDto;
import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.model.StudentCurrentLevel;
import com.dilaw.mohe.model.StudentDegree;
import com.dilaw.mohe.model.StudentMajor;
import com.dilaw.mohe.model.StudentStudyStatus;
import com.dilaw.mohe.repository.StudentCurrentLevelRepository;
import com.dilaw.mohe.repository.StudentDegreeRepository;
import com.dilaw.mohe.repository.StudentMajorRepository;
import com.dilaw.mohe.repository.StudentStudyStatusRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.util.LocalizedLookupOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class AcademicPerformanceMetadataService {

    @Autowired
    private StudentMajorRepository majorRepository;

    @Autowired
    private StudentCurrentLevelRepository currentLevelRepository;

    @Autowired
    private StudentStudyStatusRepository studyStatusRepository;

    @Autowired
    private StudentDegreeRepository degreeRepository;

    @Autowired
    private CountryNameService countryNameService;

    @Autowired
    private CountryScopeService countryScopeService;

    public AcademicPerformanceMetadataDto getMetadata() {
        CountryScopeService.CountryScope scope = countryScopeService.getScope();
        List<CountryNameService.CountryRef> countries = countryNameService.all().stream()
                .filter(c -> scope.isMinistryUser() || scope.getAllowedCountryIds().contains(c.id()))
                .toList();

        return AcademicPerformanceMetadataDto.builder()
                .majors(LocalizedLookupOptions.of(majorRepository.findAll(), StudentMajor::getId))
                .levels(LocalizedLookupOptions.of(currentLevelRepository.findAll(), StudentCurrentLevel::getId))
                .statuses(LocalizedLookupOptions.of(studyStatusRepository.findAll(), StudentStudyStatus::getId))
                .degrees(LocalizedLookupOptions.of(degreeRepository.findAll(), StudentDegree::getId))
                .countries(mapAndSort(countries.stream()
                        .map(this::mapCountry)
                        .collect(Collectors.toList())))
                .build();
    }

    private LookupOptionDto mapCountry(CountryNameService.CountryRef country) {
        // name(...) rather than a raw name: mapAndSort's comparator would NPE on a null name.
        return new LookupOptionDto(country.id(), country.name(!LocalizedLookupOptions.english()));
    }

    private List<LookupOptionDto> mapAndSort(List<LookupOptionDto> options) {
        return options.stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .collect(Collectors.toList());
    }
}
