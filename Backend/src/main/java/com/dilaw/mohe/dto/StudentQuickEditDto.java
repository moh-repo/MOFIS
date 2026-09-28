package com.dilaw.mohe.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class StudentQuickEditDto {
    
    // From STUDENT_DATA
    private Integer studentId;
    private String studentName;
    private String studentDelegationDecisionYear;  // String in DB
    private Integer studentDegreeId;
    private Integer studentMajorId;
    private Integer studentDelegateDecisionNo;     // Integer in DB
    private Integer studentStudyNoOfYears;
    private Integer studentCurrentLevelId;
    private Integer studentFirstReceiptOfDue;      // Integer in DB
    private Integer studentAmount;
    private String studentNote1;
    private String studentNote2;
    private Integer studentStudyStatusId;
    private Integer studentAddedQuarter;
    private Integer studentGender;
    private String studentPassportNumber;
    private Integer studyUniversityFeeId;
    private Integer studyUniversityFeeStatusId;
    private Integer studentLastQuarter;
    private String currentQuarter;
    private Integer studentAgencyTypeId;
    private Integer studentMinistryTypeId;
    private Integer studentStudyTypeId;
    private Integer studentUniversityTypeId;
    private String studentBirthdatePlace;
    private Integer studentPassportIssueCountryId;
    private String studentEmail;
    private String studentCellphone;
    private String studentHomeNumber;
    private String studentEmergencyContact;

    // From STUDENT_FINANCE
    private String studentFinId;  // String in DB
    private String studentFinName;
    private Integer studentFinRecievedYear;
    private Integer studentFinRecievedMonth;
    private Integer studentFinRecievedYearMonth;
    private Integer studentFinRecievedAmount;
    private Integer studyCountryInId;
    private Integer studyGovInId;
    private Integer studyFinanceBankeId;
    private Integer studyFinanceBankBranchId;
    private Integer studyFinanceDofId;
    private Integer studyFinRecievedStatus;
    private String studyNote1;
    private Integer studyReceivedWhomStatusId;
    private Integer studyReceivedWhomId;
}

