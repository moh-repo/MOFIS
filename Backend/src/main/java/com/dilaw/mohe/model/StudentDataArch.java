package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_DATA_ARCH", schema = "MOHECORE")
public class StudentDataArch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Integer id;

    /** The live STUDENT_DATA.STUDENT_ID this snapshot was taken from (repeatable across rows). */
    @Column(name = "STUDENT_ID")
    private Integer studentId;

    /*
     * Totals across this student's archived finance rows, for the archive list's
     * Total Received / Total Wafar / Total Net columns. Not columns on this table —
     * they live in STUDENT_FINANCE_ARCH and are filled in per page by
     * StudentDataArchService.withArchivedFinanceTotals(). Deliberately distinct from
     * studentAmount below, which is the archived allowance, not a disbursement.
     */
    @Transient
    private Integer archivedAmountReceived;

    @Transient
    private Integer archivedWafarAmount;

    @Transient
    private Integer archivedNetAmount;

    @Nationalized
    @Column(name = "STUDENT_NAME")
    private String studentName;

    @ColumnDefault("0")
    @Column(name = "STUDENT_COUNTRY_ID")
    private Integer studentCountryId;

    @ColumnDefault("0")
    @Column(name = "STUDENT_GOV_ID")
    private Integer studentGovId;

    @ColumnDefault("0")
    @Column(name = "STUDENT_MUD_ID")
    private Integer studentMudId;

    @Nationalized
    @Column(name = "STUDENT_DELEGATION_DECISION_YEAR")
    private String studentDelegationDecisionYear;

    @ColumnDefault("0")
    @Column(name = "STUDENT_DEGREE_ID")
    private Integer studentDegreeId;

    @ColumnDefault("0")
    @Column(name = "STUDENT_MAJOR_ID")
    private Integer studentMajorId;

    @Nationalized
    @Column(name = "STUDENT_MAJOR_NAME")
    private String studentMajorName;

    @ColumnDefault("0")
    @Column(name = "STUDENT_DELEGATE_DECISION_NO")
    private Integer studentDelegateDecisionNo;

    @ColumnDefault("0")
    @Column(name = "STUDENT_STUDY_NO_OF_YEARS")
    private Integer studentStudyNoOfYears;

    @ColumnDefault("0")
    @Column(name = "STUDENT_CURRENT_LEVEL_ID")
    private Integer studentCurrentLevelId;

    @ColumnDefault("0")
    @Column(name = "STUDENT_FIRST_RECEIPT_OF_DUE")
    private Integer studentFirstReceiptOfDue;

    @ColumnDefault("0")
    @Column(name = "STUDENT_AMOUNT")
    private Integer studentAmount;

    @Nationalized
    @Lob
    @Column(name = "STUDENT_NOTE_1")
    private String studentNote1;

    @Nationalized
    @Lob
    @Column(name = "STUDENT_NOTE_2")
    private String studentNote2;

    @ColumnDefault("0")
    @Column(name = "STUDENT_STUDY_STATUS_ID")
    private Integer studentStudyStatusId;

    @ColumnDefault("0")
    @Column(name = "STUDENT_AGENCY_TYPE_ID")
    private Integer studentAgencyTypeId;

    @ColumnDefault("0")
    @Column(name = "STUDENT_MINISTRY_TYPE_ID")
    private Integer studentMinistryTypeId;

    @ColumnDefault("0")
    @Column(name = "STUDENT_STUDY_TYPE_ID")
    private Integer studentStudyTypeId;

    @ColumnDefault("0")
    @Column(name = "STUDENT_UNIVERSITY_TYPE_ID")
    private Integer studentUniversityTypeId;

    @ColumnDefault("0")
    @Column(name = "STUDENT_ADDED_QUARTER")
    private Integer studentAddedQuarter;

    @ColumnDefault("0")
    @Column(name = "STUDENT_LAST_QUARTER")
    private Integer studentLastQuarter;

    /** Current quarter as a YYYYQ code (year + quarter number), e.g. "20242". */
    @Nationalized
    @Column(name = "CURRENT_QUARTER")
    private String currentQuarter;

    @ColumnDefault("0")
    @Column(name = "STUDY_UNIVERSITY_IN")
    private Integer studyUniversityIn;

    @ColumnDefault("999")
    @Column(name = "STUDY_COLLEGE_IN")
    private Integer studyCollegeIn;

    @ColumnDefault("0")
    @Column(name = "STUDY_COUNTRY_IN")
    private Integer studyCountryIn;

    @ColumnDefault("0")
    @Column(name = "SYUDY_GOV_IN")
    private Integer syudyGovIn;

    @ColumnDefault("0")
    @Column(name = "STUDY_EDUCATION_IN")
    private Integer studyEducationIn;

    @ColumnDefault("0")
    @Column(name = "STUDY_MAJOR_IN")
    private Integer studyMajorIn;

    @Column(name = "STUDENT_BIRTHDATE")
    private Instant studentBirthdate;

    @Nationalized
    @Column(name = "STUDENT_BIRTHDATE_PLACE")
    private String studentBirthdatePlace;

    @ColumnDefault("0")
    @Column(name = "STUDENT_GENDER")
    private Integer studentGender;

    @Nationalized
    @Column(name = "STUDENT_PASSPORT_NUMBER")
    private String studentPassportNumber;

    @Column(name = "STUDENT_PASSPORT_ISSUE_DATE")
    private Instant studentPassportIssueDate;

    @Column(name = "STUDENT_PASSPORT_EXPIRE_DATE")
    private Instant studentPassportExpireDate;

    @ColumnDefault("0")
    @Column(name = "STUDENT_PASSPORT_ISSUE_COUNTRY_ID")
    private Integer studentPassportIssueCountryId;

    @ColumnDefault("0")
    @Column(name = "STUDY_UNIVERSITY_FEE_ID")
    private Integer studyUniversityFeeId;

    @ColumnDefault("0")
    @Column(name = "STUDY_UNIVERSITY_FEE_STATUS_ID")
    private Integer studyUniversityFeeStatusId;

    @ColumnDefault("getdate()")
    @Column(name = "STUDENT_MIDATE")
    private Instant studentMidate;

    @ColumnDefault("getdate()")
    @Column(name = "ARCHIVE_DATE")
    private Instant archiveDate;

    /** When this row was written to the archive. */
    @ColumnDefault("getdate()")
    @Column(name = "INSERTED_TO_ARCH_DATE")
    private Instant insertedToArchDate;

    @ColumnDefault("0")
    @Column(name = "QUERTER_IS")
    private Integer querterIs;

    /** Full-profile JSON snapshot captured at archive time (null for historical rows). */
    @Nationalized
    @Lob
    @Column(name = "PROFILE_SNAPSHOT")
    private String profileSnapshot;

    @ColumnDefault("0")
    @Column(name = "AMOUNT_CURRENCY")
    private Integer amountCurrency;

    @Column(name = "STUDENT_AGE")
    private String studentAge;

    @ColumnDefault("0")
    @Column(name = "UNIVERSITY_FEE_STATUS_CURRENCY")
    private Integer universityFeeStatusCurrency;

    @Nationalized
    @Column(name = "PICTURE_PATH")
    private String picturePath;

    @Nationalized
    @Column(name = "STUDENT_EMAIL")
    private String studentEmail;

    @Nationalized
    @Column(name = "STUDENT_CELLPHONE")
    private String studentCellphone;

    @Nationalized
    @Column(name = "STUDENT_HOME_NUMBER")
    private String studentHomeNumber;

    @Nationalized
    @Column(name = "STUDENT_EMERGENCY_CONTACT")
    private String studentEmergencyContact;

    @ColumnDefault("0")
    @Column(name = "IS_DELETED")
    private Boolean isDeleted;

    @Column(name = "DELETED_DATE")
    private Instant deletedDate;

    @Nationalized
    @ColumnDefault("'ALL'")
    @Column(name = "RECORD_VISIBILITY")
    private String recordVisibility;

    @Nationalized
    @Column(name = "CREATED_ROLE_LEVEL")
    private String createdRoleLevel;

}