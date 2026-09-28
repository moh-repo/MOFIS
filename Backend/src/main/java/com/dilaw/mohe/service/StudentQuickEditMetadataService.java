package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.dto.StudentQuickEditMetadataDto;
import com.dilaw.mohe.model.*;
import com.dilaw.mohe.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class StudentQuickEditMetadataService {

    @Autowired
    private StudentGenderRepository genderRepository;

    @Autowired
    private StudentStudyStatusRepository statusRepository;

    @Autowired
    private StudentDegreeRepository degreeRepository;

    @Autowired
    private StudentMajorRepository majorRepository;

    @Autowired
    private StudentCurrentLevelRepository levelRepository;

    @Autowired
    private CountryNameService countryNameService;

    @Autowired
    private StudyGovInRepository govRepository;

    @Autowired
    private StudyFinanceBankRepository bankRepository;

    @Autowired
    private StudyFinanceBankBranchRepository bankBranchRepository;

    @Autowired
    private StudyFinanceDofRepository dofRepository;

    @Autowired
    private StudyPayFeeInStatusRepository universityFeeStatusRepository;

    @Autowired
    private StudyFinReceivedStatusRepository finReceivedStatusRepository;

    @Autowired
    private StudyReceivedWhomStatusRepository receivedWhomStatusRepository;

    @Autowired
    private StudentAgencyTypeRepository agencyTypeRepository;

    @Autowired
    private StudentMinistryTypeRepository ministryTypeRepository;

    @Autowired
    private StudentStudyTypeRepository studyTypeRepository;

    @Autowired
    private StudentUniversityTypeRepository universityTypeRepository;

    @Autowired
    private StudentCountryRepository homeCountryRepository;

    @Autowired
    private StudyFinReceivedWhomRepository finReceivedWhomRepository;

    public StudentQuickEditMetadataDto getMetadata() {
        return StudentQuickEditMetadataDto.builder()
                .genders(genderRepository.findAll().stream()
                        .filter(e -> active(e.getIsActive()))
                        .map(this::mapGender)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .statuses(statusRepository.findAll().stream()
                        .filter(e -> active(e.getIsActive()))
                        .map(this::mapStatus)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .degrees(degreeRepository.findAll().stream()
                        .filter(e -> active(e.getIsActive()))
                        .map(this::mapDegree)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .majors(majorRepository.findAll().stream()
                        .filter(e -> active(e.getIsActive()))
                        .map(this::mapMajor)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .levels(levelRepository.findAll().stream()
                        .filter(e -> active(e.getIsActive()))
                        .map(this::mapLevel)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .countries(countryNameService.all().stream()
                        .map(this::mapCountry)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .governments(govRepository.findAll().stream()
                        .filter(e -> active(e.getIsActive()))
                        .map(this::mapGovernment)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .banks(bankRepository.findAll().stream()
                        .filter(e -> active(e.getIsActive()))
                        .map(this::mapBank)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .bankBranches(bankBranchRepository.findAll().stream()
                        .filter(e -> active(e.getIsActive()))
                        .map(this::mapBankBranch)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .dofs(dofRepository.findAll().stream()
                        .filter(e -> active(e.getIsActive()))
                        .map(this::mapDof)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .universityFeeStatuses(universityFeeStatusRepository.findAll().stream()
                        .filter(e -> active(e.getIsActive()))
                        .map(this::mapUniversityFeeStatus)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .finReceivedStatuses(finReceivedStatusRepository.findAll().stream()
                        .filter(e -> active(e.getIsActive()))
                        .map(this::mapFinReceivedStatus)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .receivedWhomStatuses(receivedWhomStatusRepository.findAll().stream()
                        .filter(e -> active(e.getIsActive()))
                        .map(this::mapReceivedWhomStatus)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .agencyTypes(agencyTypeRepository.findAll().stream()
                        .filter(e -> active(e.getIsActive()))
                        .map(this::mapAgencyType)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .ministryTypes(ministryTypeRepository.findAll().stream()
                        .filter(e -> active(e.getIsActive()))
                        .map(this::mapMinistryType)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .studyTypes(studyTypeRepository.findAll().stream()
                        .filter(e -> active(e.getIsActive()))
                        .map(this::mapStudyType)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .universityTypes(universityTypeRepository.findAll().stream()
                        .filter(e -> active(e.getIsActive()))
                        .map(this::mapUniversityType)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .homeCountries(homeCountryRepository.findAll().stream()
                        .filter(e -> active(e.getIsActive()))
                        .map(this::mapHomeCountry)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .finReceivedWhoms(finReceivedWhomRepository.findAll().stream()
                        .map(this::mapFinReceivedWhom)
                        .sorted(Comparator.comparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList()))
                .build();
    }

    /**
     * The reader's language, from the request's Accept-Language. The lists were
     * Arabic whatever the screen's language, and included switched-off rows — the
     * gender list offered an inactive "test" (QA B5, UI-001).
     */
    private static boolean english() {
        return "en".equalsIgnoreCase(LocaleContextHolder.getLocale().getLanguage());
    }

    /** The English name in English when there is one; the Arabic otherwise, and the other way round. */
    private static String name(String ar, String en) {
        boolean hasAr = ar != null && !ar.isBlank();
        boolean hasEn = en != null && !en.isBlank();
        if (english()) {
            return hasEn ? en : (hasAr ? ar : "");
        }
        return hasAr ? ar : (hasEn ? en : "");
    }

    private static boolean active(Boolean isActive) {
        return !Boolean.FALSE.equals(isActive);
    }

    private LookupOptionDto mapGender(StudentGender gender) {
        return new LookupOptionDto(gender.getId(), name(gender.getStudentGenderName(), gender.getStudentGenderNameEn()));
    }

    private LookupOptionDto mapStatus(StudentStudyStatus status) {
        return new LookupOptionDto(status.getId(), name(status.getStudentStudyStatusName(), status.getStudentStudyStatusNameEn()));
    }

    private LookupOptionDto mapDegree(StudentDegree degree) {
        return new LookupOptionDto(degree.getId(), name(degree.getStudentDegreeName(), degree.getStudentDegreeNameEn()));
    }

    private LookupOptionDto mapMajor(StudentMajor major) {
        return new LookupOptionDto(major.getId(), name(major.getStudentMajorName(), major.getStudentMajorNameEn()));
    }

    private LookupOptionDto mapLevel(StudentCurrentLevel level) {
        return new LookupOptionDto(level.getId(), name(level.getStudentCurrentLevelName(), level.getStudentCurrentLevelNameEn()));
    }

    private LookupOptionDto mapCountry(CountryNameService.CountryRef country) {
        // name(true) rather than raw nameAr(): the comparator above would NPE on a null name.
        return new LookupOptionDto(country.id(), country.name(!english()));
    }

    private LookupOptionDto mapGovernment(StudyGovIn gov) {
        return new LookupOptionDto(gov.getId(), name(gov.getStudyGovInName(), gov.getStudyGovInNameEn()));
    }

    private LookupOptionDto mapBank(StudyFinanceBank bank) {
        return new LookupOptionDto(bank.getId(), name(bank.getStudyFinanceBankeName(), bank.getStudyFinanceBankeNameEn()));
    }

    private LookupOptionDto mapBankBranch(StudyFinanceBankBranch branch) {
        return new LookupOptionDto(branch.getId(), name(branch.getStudyFinanceBankBranchName(), branch.getStudyFinanceBankBranchNameEn()));
    }

    private LookupOptionDto mapDof(StudyFinanceDof dof) {
        return new LookupOptionDto(dof.getId(), name(dof.getStudyFinanceDofName(), dof.getStudyFinanceDofNameEn()));
    }

    private LookupOptionDto mapUniversityFeeStatus(StudyPayFeeInStatus status) {
        return new LookupOptionDto(status.getId(), name(status.getStudyPayFeeInStatusName(), status.getStudyPayFeeInStatusNameEn()));
    }

    private LookupOptionDto mapFinReceivedStatus(StudyFinReceivedStatus status) {
        return new LookupOptionDto(status.getId(), name(status.getStudyFinReceivedStatusName(), status.getStudyFinReceivedStatusNameEn()));
    }

    private LookupOptionDto mapReceivedWhomStatus(StudyReceivedWhomStatus status) {
        return new LookupOptionDto(status.getId(), name(status.getStudyReceivedWhomStatusName(), status.getStudyReceivedWhomStatusNameEn()));
    }

    private LookupOptionDto mapAgencyType(StudentAgencyType t) {
        return new LookupOptionDto(t.getId(), name(t.getStudentAgencyTypeName(), t.getStudentAgencyTypeNameEn()));
    }

    private LookupOptionDto mapMinistryType(StudentMinistryType t) {
        return new LookupOptionDto(t.getId(), name(t.getStudentMinistryTypeName(), t.getStudentMinistryTypeNameEn()));
    }

    private LookupOptionDto mapStudyType(StudentStudyType t) {
        return new LookupOptionDto(t.getId(), name(t.getStudentStudyTypeName(), t.getStudentStudyTypeNameEn()));
    }

    private LookupOptionDto mapUniversityType(StudentUniversityType t) {
        return new LookupOptionDto(t.getId(), name(t.getStudentUniversityTypeName(), t.getStudentUniversityTypeNameEn()));
    }

    private LookupOptionDto mapHomeCountry(StudentCountry c) {
        return new LookupOptionDto(c.getStudentCountryId(), name(c.getStudentCountryName(), c.getStudentCountryNameEn()));
    }

    private LookupOptionDto mapFinReceivedWhom(StudyFinReceivedWhom w) {
        String name = w.getFinReceivedName() != null ? w.getFinReceivedName() : "#" + w.getId();
        return new LookupOptionDto(w.getId(), name);
    }
}

