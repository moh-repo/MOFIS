package com.dilaw.mohe.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** API response for student list report view — no JPA entity exposure. */
@Getter
@Setter
public class StudentListDetailedDto {
    private Integer studentId;
    private String studentName;
    private String studentPassportNumber;
    private LocalDate studentBirthdate;
    private Integer studentAge;
    private Integer studentCountryId;
    private String studentCountryName;
    private String studentGender;
    private Integer studentMajorId;
    private String studentMajorName;
    private Integer studentDegreeId;
    private String studentDegreeName;
    private Integer studentCurrentLevelId;
    private String studentCurrentLevelName;
    private Integer studentStudyTypeId;
    private String studentStudyTypeName;
    private Integer studentStudyStatusId;
    private String studentStudyStatusName;
    private Integer studentUniversityTypeId;
    private String studentUniversityTypeName;
    private Integer studentAgencyTypeId;
    private String studentAgencyTypeName;
    private Integer studentMinistryTypeId;
    private String studentMinistryTypeName;
    private Integer studentStudyNoOfYears;
    private String studentDelegationDecisionYear;
    private Integer studentAddedQuarter;
    private LocalDate studentPassportIssueDate;
    private LocalDate studentPassportExpireDate;
}
