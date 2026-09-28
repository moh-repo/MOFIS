package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.dto.StudentListMetadataDto;
import com.dilaw.mohe.model.StudentAgencyType;
import com.dilaw.mohe.model.StudentCurrentLevel;
import com.dilaw.mohe.model.StudentDegree;
import com.dilaw.mohe.model.StudentGender;
import com.dilaw.mohe.model.StudentMajor;
import com.dilaw.mohe.model.StudentMinistryType;
import com.dilaw.mohe.model.StudentStudyStatus;
import com.dilaw.mohe.model.StudentStudyType;
import com.dilaw.mohe.model.StudentUniversityType;
import com.dilaw.mohe.repository.StudentAgencyTypeRepository;
import com.dilaw.mohe.repository.StudentCurrentLevelRepository;
import com.dilaw.mohe.repository.StudentDegreeRepository;
import com.dilaw.mohe.repository.StudentGenderRepository;
import com.dilaw.mohe.repository.StudentMajorRepository;
import com.dilaw.mohe.repository.StudentMinistryTypeRepository;
import com.dilaw.mohe.repository.StudentStudyStatusRepository;
import com.dilaw.mohe.repository.StudentStudyTypeRepository;
import com.dilaw.mohe.repository.StudentUniversityTypeRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.util.LocalizedLookupOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class StudentListMetadataService {

    @Autowired
    private CountryNameService countryNameService;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private StudentGenderRepository genderRepository;

    @Autowired
    private StudentMajorRepository majorRepository;

    @Autowired
    private StudentDegreeRepository degreeRepository;

    @Autowired
    private StudentCurrentLevelRepository currentLevelRepository;

    @Autowired
    private StudentStudyTypeRepository studyTypeRepository;

    @Autowired
    private StudentStudyStatusRepository studyStatusRepository;

    @Autowired
    private StudentUniversityTypeRepository universityTypeRepository;

    @Autowired
    private StudentAgencyTypeRepository agencyTypeRepository;

    @Autowired
    private StudentMinistryTypeRepository ministryTypeRepository;

    public StudentListMetadataDto getMetadata() {
        CountryScopeService.CountryScope scope = countryScopeService.getScope();
        var countries = countryNameService.all().stream()
                .filter(c -> scope.isMinistryUser() || scope.getAllowedCountryIds().contains(c.id()))
                .toList();

        return StudentListMetadataDto.builder()
                .countries(countries.stream()
                        .map(this::mapCountry)
                        .sorted(this::compareByName)
                        .collect(Collectors.toList()))
                .genders(LocalizedLookupOptions.of(genderRepository.findAll(), StudentGender::getId))
                .majors(LocalizedLookupOptions.of(majorRepository.findAll(), StudentMajor::getId))
                .degrees(LocalizedLookupOptions.of(degreeRepository.findAll(), StudentDegree::getId))
                .levels(LocalizedLookupOptions.of(currentLevelRepository.findAll(), StudentCurrentLevel::getId))
                .studyTypes(LocalizedLookupOptions.of(studyTypeRepository.findAll(), StudentStudyType::getId))
                .statuses(LocalizedLookupOptions.of(studyStatusRepository.findAll(), StudentStudyStatus::getId))
                .universityTypes(LocalizedLookupOptions.of(universityTypeRepository.findAll(), StudentUniversityType::getId))
                .agencyTypes(LocalizedLookupOptions.of(agencyTypeRepository.findAll(), StudentAgencyType::getId))
                .ministryTypes(LocalizedLookupOptions.of(ministryTypeRepository.findAll(), StudentMinistryType::getId))
                .build();
    }

    private LookupOptionDto mapCountry(CountryNameService.CountryRef country) {
        // name(...) rather than a raw name: compareByName would NPE on a null name.
        return new LookupOptionDto(country.id(), country.name(!LocalizedLookupOptions.english()));
    }

    private int compareByName(LookupOptionDto a, LookupOptionDto b) {
        return a.getName().compareToIgnoreCase(b.getName());
    }
}
