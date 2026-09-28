package com.dilaw.mohe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * Read-only mapping of V_STUDENT_PROFILE_DOC (V016): one row per student with
 * every lookup FK resolved to paired *_NAME_AR / *_NAME_EN columns. Backs the
 * official single-student documents (ID card, registration form, certificate).
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "V_STUDENT_PROFILE_DOC", schema = "MOHECORE")
public class StudentProfileDoc {

    @Id
    @Column(name = "STUDENT_ID")
    private Integer studentId;

    @Column(name = "STUDENT_NAME")
    private String studentName;

    @Column(name = "PICTURE_PATH")
    private String picturePath;

    @Column(name = "STUDENT_MIDATE")
    private Instant studentMidate;

    // ── Personal ──────────────────────────────────────────────────────────────
    @Column(name = "STUDENT_GENDER_ID")
    private Integer studentGenderId;

    @Column(name = "GENDER_NAME_AR")
    private String genderNameAr;

    @Column(name = "GENDER_NAME_EN")
    private String genderNameEn;

    @Column(name = "STUDENT_BIRTHDATE")
    private Instant studentBirthdate;

    @Column(name = "STUDENT_BIRTHDATE_PLACE")
    private String studentBirthdatePlace;

    @Column(name = "STUDENT_AGE")
    private String studentAge;

    // ── Home country / governorate / municipality ─────────────────────────────
    @Column(name = "STUDENT_COUNTRY_ID")
    private Integer studentCountryId;

    @Column(name = "HOME_COUNTRY_NAME_AR")
    private String homeCountryNameAr;

    @Column(name = "HOME_COUNTRY_NAME_EN")
    private String homeCountryNameEn;

    @Column(name = "STUDENT_GOV_ID")
    private Integer studentGovId;

    @Column(name = "HOME_GOV_NAME_AR")
    private String homeGovNameAr;

    @Column(name = "HOME_GOV_NAME_EN")
    private String homeGovNameEn;

    @Column(name = "STUDENT_MUD_ID")
    private Integer studentMudId;

    @Column(name = "HOME_MUD_NAME_AR")
    private String homeMudNameAr;

    @Column(name = "HOME_MUD_NAME_EN")
    private String homeMudNameEn;

    // ── Passport ──────────────────────────────────────────────────────────────
    @Column(name = "STUDENT_PASSPORT_NUMBER")
    private String studentPassportNumber;

    @Column(name = "STUDENT_PASSPORT_ISSUE_DATE")
    private Instant studentPassportIssueDate;

    @Column(name = "STUDENT_PASSPORT_EXPIRE_DATE")
    private Instant studentPassportExpireDate;

    @Column(name = "STUDENT_PASSPORT_ISSUE_COUNTRY_ID")
    private Integer studentPassportIssueCountryId;

    @Column(name = "PASSPORT_COUNTRY_NAME_AR")
    private String passportCountryNameAr;

    @Column(name = "PASSPORT_COUNTRY_NAME_EN")
    private String passportCountryNameEn;

    // ── Academic ──────────────────────────────────────────────────────────────
    @Column(name = "STUDENT_DEGREE_ID")
    private Integer studentDegreeId;

    @Column(name = "DEGREE_NAME_AR")
    private String degreeNameAr;

    @Column(name = "DEGREE_NAME_EN")
    private String degreeNameEn;

    @Column(name = "STUDENT_MAJOR_ID")
    private Integer studentMajorId;

    @Column(name = "MAJOR_NAME_AR")
    private String majorNameAr;

    @Column(name = "MAJOR_NAME_EN")
    private String majorNameEn;

    @Column(name = "STUDENT_CURRENT_LEVEL_ID")
    private Integer studentCurrentLevelId;

    @Column(name = "LEVEL_NAME_AR")
    private String levelNameAr;

    @Column(name = "LEVEL_NAME_EN")
    private String levelNameEn;

    @Column(name = "STUDENT_STUDY_TYPE_ID")
    private Integer studentStudyTypeId;

    @Column(name = "STUDY_TYPE_NAME_AR")
    private String studyTypeNameAr;

    @Column(name = "STUDY_TYPE_NAME_EN")
    private String studyTypeNameEn;

    @Column(name = "STUDENT_STUDY_STATUS_ID")
    private Integer studentStudyStatusId;

    @Column(name = "STUDY_STATUS_NAME_AR")
    private String studyStatusNameAr;

    @Column(name = "STUDY_STATUS_NAME_EN")
    private String studyStatusNameEn;

    @Column(name = "STUDENT_UNIVERSITY_TYPE_ID")
    private Integer studentUniversityTypeId;

    @Column(name = "UNIVERSITY_TYPE_NAME_AR")
    private String universityTypeNameAr;

    @Column(name = "UNIVERSITY_TYPE_NAME_EN")
    private String universityTypeNameEn;

    @Column(name = "STUDENT_AGENCY_TYPE_ID")
    private Integer studentAgencyTypeId;

    @Column(name = "AGENCY_TYPE_NAME_AR")
    private String agencyTypeNameAr;

    @Column(name = "AGENCY_TYPE_NAME_EN")
    private String agencyTypeNameEn;

    @Column(name = "STUDENT_MINISTRY_TYPE_ID")
    private Integer studentMinistryTypeId;

    @Column(name = "MINISTRY_TYPE_NAME_AR")
    private String ministryTypeNameAr;

    @Column(name = "MINISTRY_TYPE_NAME_EN")
    private String ministryTypeNameEn;

    @Column(name = "STUDENT_STUDY_NO_OF_YEARS")
    private Integer studentStudyNoOfYears;

    // ── Delegation decision ───────────────────────────────────────────────────
    @Column(name = "STUDENT_DELEGATE_DECISION_NO")
    private Integer studentDelegateDecisionNo;

    @Column(name = "STUDENT_DELEGATION_DECISION_YEAR")
    private String studentDelegationDecisionYear;

    // ── Study destination ─────────────────────────────────────────────────────
    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;

    @Column(name = "STUDY_COUNTRY_NAME_AR")
    private String studyCountryNameAr;

    @Column(name = "STUDY_COUNTRY_NAME_EN")
    private String studyCountryNameEn;

    @Column(name = "STUDY_GOV_IN_ID")
    private Integer studyGovInId;

    @Column(name = "STUDY_GOV_NAME_AR")
    private String studyGovNameAr;

    @Column(name = "STUDY_GOV_NAME_EN")
    private String studyGovNameEn;

    @Column(name = "STUDY_UNIVERSITY_IN_ID")
    private Integer studyUniversityInId;

    @Column(name = "STUDY_UNIVERSITY_NAME_AR")
    private String studyUniversityNameAr;

    @Column(name = "STUDY_UNIVERSITY_NAME_EN")
    private String studyUniversityNameEn;

    @Column(name = "STUDY_COLLEGE_IN_ID")
    private Integer studyCollegeInId;

    @Column(name = "STUDY_COLLEGE_NAME_AR")
    private String studyCollegeNameAr;

    @Column(name = "STUDY_COLLEGE_NAME_EN")
    private String studyCollegeNameEn;

    @Column(name = "STUDY_EDUCATION_IN_ID")
    private Integer studyEducationInId;

    @Column(name = "STUDY_EDUCATION_NAME_AR")
    private String studyEducationNameAr;

    @Column(name = "STUDY_EDUCATION_NAME_EN")
    private String studyEducationNameEn;

    @Column(name = "STUDY_MAJOR_IN_ID")
    private Integer studyMajorInId;

    @Column(name = "STUDY_MAJOR_IN_NAME_AR")
    private String studyMajorInNameAr;

    @Column(name = "STUDY_MAJOR_IN_NAME_EN")
    private String studyMajorInNameEn;

    // ── Scholarship finance summary ───────────────────────────────────────────
    @Column(name = "STUDENT_AMOUNT")
    private Integer studentAmount;

    @Column(name = "AMOUNT_CURRENCY_ID")
    private Integer amountCurrencyId;

    @Column(name = "CURRENCY_NAME_AR")
    private String currencyNameAr;

    @Column(name = "CURRENCY_NAME_EN")
    private String currencyNameEn;

    // ── Contacts ──────────────────────────────────────────────────────────────
    @Column(name = "STUDENT_EMAIL")
    private String studentEmail;

    @Column(name = "STUDENT_CELLPHONE")
    private String studentCellphone;

    @Column(name = "STUDENT_HOME_NUMBER")
    private String studentHomeNumber;

    @Column(name = "STUDENT_EMERGENCY_CONTACT")
    private String studentEmergencyContact;

    // ── Quarters ──────────────────────────────────────────────────────────────
    @Column(name = "STUDENT_ADDED_QUARTER")
    private Integer studentAddedQuarter;

    @Column(name = "STUDENT_LAST_QUARTER")
    private Integer studentLastQuarter;

    @Column(name = "CURRENT_QUARTER")
    private String currentQuarter;
}
