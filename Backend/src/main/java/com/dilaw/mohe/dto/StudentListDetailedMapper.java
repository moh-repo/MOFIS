package com.dilaw.mohe.dto;

import com.dilaw.mohe.model.StudentListDetailed;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.stream.Collectors;

public final class StudentListDetailedMapper {

    private StudentListDetailedMapper() {
    }

    public static StudentListDetailedDto toDto(StudentListDetailed entity) {
        if (entity == null) {
            return null;
        }
        StudentListDetailedDto dto = new StudentListDetailedDto();
        dto.setStudentId(entity.getStudentId());
        dto.setStudentName(entity.getStudentName());
        dto.setStudentPassportNumber(entity.getStudentPassportNumber());
        dto.setStudentBirthdate(entity.getStudentBirthdate());
        dto.setStudentAge(entity.getStudentAge());
        dto.setStudentCountryId(entity.getStudentCountryId());
        dto.setStudentCountryName(entity.getStudentCountryName());
        dto.setStudentGender(entity.getStudentGender());
        dto.setStudentMajorId(entity.getStudentMajorId());
        dto.setStudentMajorName(entity.getStudentMajorName());
        dto.setStudentDegreeId(entity.getStudentDegreeId());
        dto.setStudentDegreeName(entity.getStudentDegreeName());
        dto.setStudentCurrentLevelId(entity.getStudentCurrentLevelId());
        dto.setStudentCurrentLevelName(entity.getStudentCurrentLevelName());
        dto.setStudentStudyTypeId(entity.getStudentStudyTypeId());
        dto.setStudentStudyTypeName(entity.getStudentStudyTypeName());
        dto.setStudentStudyStatusId(entity.getStudentStudyStatusId());
        dto.setStudentStudyStatusName(entity.getStudentStudyStatusName());
        dto.setStudentUniversityTypeId(entity.getStudentUniversityTypeId());
        dto.setStudentUniversityTypeName(entity.getStudentUniversityTypeName());
        dto.setStudentAgencyTypeId(entity.getStudentAgencyTypeId());
        dto.setStudentAgencyTypeName(entity.getStudentAgencyTypeName());
        dto.setStudentMinistryTypeId(entity.getStudentMinistryTypeId());
        dto.setStudentMinistryTypeName(entity.getStudentMinistryTypeName());
        dto.setStudentStudyNoOfYears(entity.getStudentStudyNoOfYears());
        dto.setStudentDelegationDecisionYear(entity.getStudentDelegationDecisionYear());
        dto.setStudentAddedQuarter(entity.getStudentAddedQuarter());
        dto.setStudentPassportIssueDate(entity.getStudentPassportIssueDate());
        dto.setStudentPassportExpireDate(entity.getStudentPassportExpireDate());
        return dto;
    }

    public static List<StudentListDetailedDto> toDtoList(List<StudentListDetailed> entities) {
        return entities.stream().map(StudentListDetailedMapper::toDto).collect(Collectors.toList());
    }

    public static Page<StudentListDetailedDto> toDtoPage(Page<StudentListDetailed> page) {
        return page.map(StudentListDetailedMapper::toDto);
    }
}
