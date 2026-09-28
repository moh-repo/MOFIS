package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.dto.StudentDemographicsMetadataDto;
import com.dilaw.mohe.model.LuReportDimension;
import com.dilaw.mohe.model.StudentCurrentLevel;
import com.dilaw.mohe.model.StudentDegree;
import com.dilaw.mohe.model.StudentGender;
import com.dilaw.mohe.model.StudentMajor;
import com.dilaw.mohe.model.StudentStudyStatus;
import com.dilaw.mohe.model.StudentStudyType;
import com.dilaw.mohe.repository.LuReportDimensionRepository;
import com.dilaw.mohe.repository.StudentCurrentLevelRepository;
import com.dilaw.mohe.repository.StudentDegreeRepository;
import com.dilaw.mohe.repository.StudentGenderRepository;
import com.dilaw.mohe.repository.StudentMajorRepository;
import com.dilaw.mohe.repository.StudentStudyStatusRepository;
import com.dilaw.mohe.repository.StudentStudyTypeRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.util.LocalizedLookupOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class StudentDemographicsMetadataService {

    /**
     * Group code for the age bands in LU_REPORT_DIMENSION.
     *
     * <p>The bands used to be a {@code private static final List.of(...)} in
     * this class, the only metadata list here that was not read from the
     * database — every other one (countries, genders, levels, study types,
     * degrees, majors, statuses) came from its lookup repository. They are now
     * seeded into LU_REPORT_DIMENSION by V028, so changing a band is a data edit
     * rather than a redeploy, and the Arabic and English labels live beside
     * every other lookup's.
     */
    private static final String AGE_RANGE_GROUP = "AGE_RANGE";

    @Autowired
    private LuReportDimensionRepository reportDimensionRepository;

    @Autowired
    private CountryNameService countryNameService;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private StudentGenderRepository genderRepository;

    @Autowired
    private StudentCurrentLevelRepository levelRepository;

    @Autowired
    private StudentStudyTypeRepository studyTypeRepository;

    @Autowired
    private StudentDegreeRepository degreeRepository;

    @Autowired
    private StudentMajorRepository majorRepository;

    @Autowired
    private StudentStudyStatusRepository statusRepository;

    public StudentDemographicsMetadataDto getMetadata() {
        CountryScopeService.CountryScope scope = countryScopeService.getScope();
        List<CountryNameService.CountryRef> countries = countryNameService.all().stream()
                .filter(c -> scope.isMinistryUser() || scope.getAllowedCountryIds().contains(c.id()))
                .toList();

        return StudentDemographicsMetadataDto.builder()
                .countries(countries.stream()
                        // name(...) rather than a raw name: the comparator below would NPE on a null name.
                        .map(c -> new LookupOptionDto(c.id(), c.name(!LocalizedLookupOptions.english())))
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .genders(LocalizedLookupOptions.of(genderRepository.findAll(), StudentGender::getId))
                .ageRanges(reportDimensionRepository
                        .findByGroupCodeAndIsDeletedFalseOrderBySortOrderAsc(AGE_RANGE_GROUP)
                        .stream()
                        .map(LuReportDimension::getCode)
                        .collect(Collectors.toList()))
                .levels(LocalizedLookupOptions.of(levelRepository.findAll(), StudentCurrentLevel::getId))
                .studyTypes(LocalizedLookupOptions.of(studyTypeRepository.findAll(), StudentStudyType::getId))
                .degrees(LocalizedLookupOptions.of(degreeRepository.findAll(), StudentDegree::getId))
                .majors(LocalizedLookupOptions.of(majorRepository.findAll(), StudentMajor::getId))
                .statuses(LocalizedLookupOptions.of(statusRepository.findAll(), StudentStudyStatus::getId))
                .build();
    }
}
