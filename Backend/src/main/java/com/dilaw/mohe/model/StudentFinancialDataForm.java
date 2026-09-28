package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Immutable
@Table(name = "Q_Stud_Financial_Data_Form", schema = "MOHECORE")
public class StudentFinancialDataForm {
    
    @Id
    @Column(name = "STUDENT_ID")
    private Integer studentId;
    
    @Column(name = "STUDENT_NAME")
    private String studentName;
    
    @Column(name = "STUDENT_COUNTRY_ID")
    private Integer studentCountryId;
    
    @Column(name = "STUDENT_DELEGATION_DECISION_YEAR")
    private String studentDelegationDecisionYear;
    
    @Column(name = "STUDENT_DEGREE_ID")
    private Integer studentDegreeId;
    
    @Column(name = "STUDENT_MAJOR_ID")
    private Integer studentMajorId;
    
    @Column(name = "STUDENT_DELEGATE_DECISION_NO")
    private Integer studentDelegateDecisionNo;
    
    @Column(name = "STUDENT_STUDY_NO_OF_YEARS")
    private Integer studentStudyNoOfYears;
    
    @Column(name = "STUDENT_CURRENT_LEVEL_ID")
    private Integer studentCurrentLevelId;
    
    @Column(name = "STUDENT_FIRST_RECEIPT_OF_DUE")
    private Integer studentFirstReceiptOfDue;
    
    @Column(name = "STUDENT_AMOUNT")
    private Integer studentAmount;
    
    @Column(name = "STUDENT_NOTE_1")
    private String studentNote1;
    
    @Column(name = "STUDENT_NOTE_2")
    private String studentNote2;
    
    @Column(name = "STUDENT_STUDY_STATUS_ID")
    private Integer studentStudyStatusId;
    
    @Column(name = "STUDENT_AGENCY_TYPE_ID")
    private Integer studentAgencyTypeId;
    
    @Column(name = "STUDENT_MINISTRY_TYPE_ID")
    private Integer studentMinistryTypeId;
    
    @Column(name = "STUDENT_STUDY_TYPE_ID")
    private Integer studentStudyTypeId;
    
    @Column(name = "STUDENT_UNIVERSITY_TYPE_ID")
    private Integer studentUniversityTypeId;
    
    @Column(name = "STUDENT_STUDY_STATUS_NAME")
    private String studentStudyStatusName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_STUDY_STATUS_NAME_EN")
    private String studentStudyStatusNameEn;
    
    @Column(name = "STUDENT_AGENCY_TYPE_NAME")
    private String studentAgencyTypeName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_AGENCY_TYPE_NAME_EN")
    private String studentAgencyTypeNameEn;
    
    @Column(name = "STUDENT_COUNTRY_NAME")
    private String studentCountryName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_COUNTRY_NAME_EN")
    private String studentCountryNameEn;
    
    @Column(name = "STUDENT_DEGREE_NAME")
    private String studentDegreeName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_DEGREE_NAME_EN")
    private String studentDegreeNameEn;
    
    @Column(name = "STUDENT_MAJOR_NAME")
    private String studentMajorName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_MAJOR_NAME_EN")
    private String studentMajorNameEn;
    
    @Column(name = "STUDENT_MINISTRY_TYPE_NAME")
    private String studentMinistryTypeName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_MINISTRY_TYPE_NAME_EN")
    private String studentMinistryTypeNameEn;
    
    @Column(name = "STUDENT_UNIVERSITY_TYPE_NAME")
    private String studentUniversityTypeName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_UNIVERSITY_TYPE_NAME_EN")
    private String studentUniversityTypeNameEn;
    
    @Column(name = "STUDY_COUNTRY_IN_NAME")
    private String studyCountryInName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDY_COUNTRY_IN_NAME_EN")
    private String studyCountryInNameEn;
    
    @Column(name = "STUDENT_FIN_RECIEVED_YEAR")
    private Integer studentFinReceivedYear;
    
    @Column(name = "STUDENT_FIN_RECIEVED_MONTH")
    private Integer studentFinReceivedMonth;
    
    @Column(name = "HEAD_TITLE")
    private String headTitle;
    
    /**
     * The quarter. STUDENT_FIN_RECIEVED_MONTH holds it (1-4) despite its name; it was
     * folded as a month, which put quarters 1-3 all in quarter 1 (round-5 QA R5-31).
     */
    public Integer getQuarter() {
        if (studentFinReceivedMonth == null || studentFinReceivedMonth < 1 || studentFinReceivedMonth > 4) return null;
        return studentFinReceivedMonth;
    }
}
