package com.dilaw.mohe.model;

import com.dilaw.mohe.config.FieldPermission;
import com.dilaw.mohe.config.SensitiveField;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.Where;

import java.time.Instant;
import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_DATA", schema = "MOHECORE")
@Where(clause = "IS_DELETED = 0 OR IS_DELETED IS NULL")
/*
 * Writes only the columns that actually changed.
 *
 * Without this Hibernate issues a full 50-column UPDATE, which re-sends every
 * foreign key on the row and makes SQL Server re-validate all of them. Several
 * FKs on STUDENT_DATA are untrusted (added WITH NOCHECK over rows that use
 * sentinel ids such as 0 / 99 / 999 for "not specified"), so editing one
 * unrelated field — a passport number, say — failed on FK_STUDENT_DATA_GOV for
 * a value the edit never touched. Updating only dirty columns leaves those
 * stale values alone. See docs/scripts/V019__fix_student_data_orphan_lookups.sql
 * for the data-side repair.
 */
@org.hibernate.annotations.DynamicUpdate
public class StudentDatum {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "STUDENT_ID", nullable = false)
    private Integer studentId;

    @Nationalized
    @Column(name = "STUDENT_NAME")
    private String studentName;

    @ColumnDefault("0")
    @Column(name = "STUDENT_COUNTRY_ID", insertable = false, updatable = false)
    private Integer studentCountryId;

    @OneToOne
    @JoinColumn(name="STUDENT_COUNTRY_ID")
    private StudentCountry StudentCountry;

    /** O-03: the national id number is masked like the passport. */
    @FieldPermission(page = "STUDENTS_ADD", field = "ADD_STUDENT_ID_NUMBER", anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
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
    @SensitiveField(anyOf = {"FINANCE.VIEW", "FINANCE.EDIT", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    @Column(name = "STUDENT_FIRST_RECEIPT_OF_DUE")
    private Integer studentFirstReceiptOfDue;

    @SensitiveField(anyOf = {"FINANCE.VIEW", "FINANCE.EDIT", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    @ColumnDefault("0")
    @Column(name = "STUDENT_AMOUNT")
    private Integer studentAmount;

    @SensitiveField(anyOf = {"FINANCE.VIEW", "FINANCE.EDIT", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    @ColumnDefault("0")
    @Column(name = "AMOUNT_CURRENCY")
    private Integer amountCurrency;

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

    @Column(name = "STUDENT_AGE")
    private String studentAge;

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

    @FieldPermission(page = "STUDENTS_LIST", field = "STUDENT_BIRTHDATE", anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    @Column(name = "STUDENT_BIRTHDATE")
    private Instant studentBirthdate;

    @SensitiveField(anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    @Nationalized
    @Column(name = "STUDENT_BIRTHDATE_PLACE")
    private String studentBirthdatePlace;

    @ColumnDefault("0")
    @Column(name = "STUDENT_GENDER")
    private Integer studentGender;

    @FieldPermission(page = "STUDENTS_ADD", field = "ADD_STUDENT_PASSPORT", anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    @Nationalized
    @Column(name = "STUDENT_PASSPORT_NUMBER")
    private String studentPassportNumber;

    @SensitiveField(anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    @Column(name = "STUDENT_PASSPORT_ISSUE_DATE")
    private Instant studentPassportIssueDate;

    @SensitiveField(anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
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

    @ColumnDefault("0")
    @Column(name = "UNIVERSITY_FEE_STATUS_CURRENCY")
    private Integer universityFeeStatusCurrency;

    @ColumnDefault("getdate()")
    @Column(name = "STUDENT_MIDATE")
    private Instant studentMidate;

    @Nationalized
    @Column(name = "PICTURE_PATH")
    private String picturePath;

    @ColumnDefault("0")
    @Column(name = "IS_DELETED", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "DELETED_DATE")
    private Instant deletedDate;

    @FieldPermission(page = "STUDENTS_LIST", field = "STUDENT_EMAIL", anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    @Nationalized
    @Column(name = "STUDENT_EMAIL")
    private String studentEmail;

    @FieldPermission(page = "STUDENTS_LIST", field = "STUDENT_PHONE", anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    @Nationalized
    @Column(name = "STUDENT_CELLPHONE")
    private String studentCellphone;

    @SensitiveField(anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    @Nationalized
    @Column(name = "STUDENT_HOME_NUMBER")
    private String studentHomeNumber;

    @FieldPermission(page = "STUDENTS_ADD", field = "ADD_STUDENT_EMERGENCY_CONTACT", anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    @Nationalized
    @Column(name = "STUDENT_EMERGENCY_CONTACT")
    private String studentEmergencyContact;


    @Column(name = "RECORD_VISIBILITY", nullable = false)
    @ColumnDefault("'ALL'")
    private String recordVisibility = "ALL";

    @Nationalized
    @Column(name = "CREATED_ROLE_LEVEL")
    private String createdRoleLevel;

// constructer


    public StudentDatum() {
    }

    public StudentDatum(
                        Integer studentId,
                        String studentName,
                        Integer studentCountryId,
                        Integer studentGovId,
                        Integer studentMudId,
                        String studentDelegationDecisionYear,
                        Integer studentDegreeId,
                        Integer studentMajorId,
                        String studentMajorName,
                        Integer studentDelegateDecisionNo,
                        Integer studentStudyNoOfYears,
                        Integer studentCurrentLevelId,
                        Integer studentFirstReceiptOfDue,
                        Integer studentAmount,
                        String studentNote1,
                        String studentNote2,
                        Integer studentStudyStatusId,
                        Integer studentAgencyTypeId,
                        Integer studentMinistryTypeId,
                        Integer studentStudyTypeId,
                        Integer studentUniversityTypeId,
                        Integer studentAddedQuarter,
                        Integer studentLastQuarter,
                        Integer studyUniversityIn,
                        Integer studyCollegeIn,
                        Integer studyCountryIn,
                        Integer syudyGovIn,
                        Integer studyEducationIn,
                        Integer studyMajorIn,
                        Instant studentBirthdate,
                        String studentBirthdatePlace,
                        Integer studentGender,
                        String studentPassportNumber,
                        Instant studentPassportIssueDate,
                        Instant studentPassportExpireDate,
                        Integer studentPassportIssueCountryId,
                        Integer studyUniversityFeeId,
                        Integer studyUniversityFeeStatusId,
                        Instant studentMidate) {
        this.studentId=studentId;
        this.studentName = studentName;
        this.studentCountryId = studentCountryId;
        this.studentGovId = studentGovId;
        this.studentMudId = studentMudId;
        this.studentDelegationDecisionYear = studentDelegationDecisionYear;
        this.studentDegreeId = studentDegreeId;
        this.studentMajorId = studentMajorId;
        this.studentMajorName = studentMajorName;
        this.studentDelegateDecisionNo = studentDelegateDecisionNo;
        this.studentStudyNoOfYears = studentStudyNoOfYears;
        this.studentCurrentLevelId = studentCurrentLevelId;
        this.studentFirstReceiptOfDue = studentFirstReceiptOfDue;
        this.studentAmount = studentAmount;
        this.studentNote1 = studentNote1;
        this.studentNote2 = studentNote2;
        this.studentStudyStatusId = studentStudyStatusId;
        this.studentAgencyTypeId = studentAgencyTypeId;
        this.studentMinistryTypeId = studentMinistryTypeId;
        this.studentStudyTypeId = studentStudyTypeId;
        this.studentUniversityTypeId = studentUniversityTypeId;
        this.studentAddedQuarter = studentAddedQuarter;
        this.studentLastQuarter = studentLastQuarter;
        this.studyUniversityIn = studyUniversityIn;
        this.studyCollegeIn = studyCollegeIn;
        this.studyCountryIn = studyCountryIn;
        this.syudyGovIn = syudyGovIn;
        this.studyEducationIn = studyEducationIn;
        this.studyMajorIn = studyMajorIn;
        this.studentBirthdate = studentBirthdate;
        this.studentBirthdatePlace = studentBirthdatePlace;
        this.studentGender = studentGender;
        this.studentPassportNumber = studentPassportNumber;
        this.studentPassportIssueDate = studentPassportIssueDate;
        this.studentPassportExpireDate = studentPassportExpireDate;
        this.studentPassportIssueCountryId = studentPassportIssueCountryId;
        this.studyUniversityFeeId = studyUniversityFeeId;
        this.studyUniversityFeeStatusId = studyUniversityFeeStatusId;
        this.studentMidate = studentMidate;

    }


// getter and setter


    public Integer getStudentId() {
        return studentId;
    }

    public void setStudentId(Integer studentId) {
        this.studentId = studentId;
    }


    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

//    public Integer getStudentCountryId() {
//        return studentCountryId;
//    }
//
//    public void setStudentCountryId(Integer studentCountryId) {
//        this.studentCountryId = studentCountryId;
//    }


    public com.dilaw.mohe.model.StudentCountry getStudentCountry() {
        return StudentCountry;
    }

    public void setStudentCountry(com.dilaw.mohe.model.StudentCountry studentCountry) {
        StudentCountry = studentCountry;
    }

    public Integer getStudentGovId() {
        return studentGovId;
    }

    public void setStudentGovId(Integer studentGovId) {
        this.studentGovId = studentGovId;
    }

    public Integer getStudentMudId() {
        return studentMudId;
    }

    public void setStudentMudId(Integer studentMudId) {
        this.studentMudId = studentMudId;
    }

    public String getStudentDelegationDecisionYear() {
        return studentDelegationDecisionYear;
    }

    public void setStudentDelegationDecisionYear(String studentDelegationDecisionYear) {
        this.studentDelegationDecisionYear = studentDelegationDecisionYear;
    }

    public Integer getStudentDegreeId() {
        return studentDegreeId;
    }

    public void setStudentDegreeId(Integer studentDegreeId) {
        this.studentDegreeId = studentDegreeId;
    }

    public Integer getStudentMajorId() {
        return studentMajorId;
    }

    public void setStudentMajorId(Integer studentMajorId) {
        this.studentMajorId = studentMajorId;
    }

    public String getStudentMajorName() {
        return studentMajorName;
    }

    public void setStudentMajorName(String studentMajorName) {
        this.studentMajorName = studentMajorName;
    }

    public Integer getStudentDelegateDecisionNo() {
        return studentDelegateDecisionNo;
    }

    public void setStudentDelegateDecisionNo(Integer studentDelegateDecisionNo) {
        this.studentDelegateDecisionNo = studentDelegateDecisionNo;
    }

    public Integer getStudentStudyNoOfYears() {
        return studentStudyNoOfYears;
    }

    public void setStudentStudyNoOfYears(Integer studentStudyNoOfYears) {
        this.studentStudyNoOfYears = studentStudyNoOfYears;
    }

    public Integer getStudentCurrentLevelId() {
        return studentCurrentLevelId;
    }

    public void setStudentCurrentLevelId(Integer studentCurrentLevelId) {
        this.studentCurrentLevelId = studentCurrentLevelId;
    }

    public Integer getStudentFirstReceiptOfDue() {
        return studentFirstReceiptOfDue;
    }

    public void setStudentFirstReceiptOfDue(Integer studentFirstReceiptOfDue) {
        this.studentFirstReceiptOfDue = studentFirstReceiptOfDue;
    }

    public Integer getStudentAmount() {
        return studentAmount;
    }

    public void setStudentAmount(Integer studentAmount) {
        this.studentAmount = studentAmount;
    }

    public String getStudentNote1() {
        return studentNote1;
    }

    public void setStudentNote1(String studentNote1) {
        this.studentNote1 = studentNote1;
    }

    public String getStudentNote2() {
        return studentNote2;
    }

    public void setStudentNote2(String studentNote2) {
        this.studentNote2 = studentNote2;
    }

    public Integer getStudentStudyStatusId() {
        return studentStudyStatusId;
    }

    public void setStudentStudyStatusId(Integer studentStudyStatusId) {
        this.studentStudyStatusId = studentStudyStatusId;
    }

    public Integer getStudentAgencyTypeId() {
        return studentAgencyTypeId;
    }

    public void setStudentAgencyTypeId(Integer studentAgencyTypeId) {
        this.studentAgencyTypeId = studentAgencyTypeId;
    }

    public Integer getStudentMinistryTypeId() {
        return studentMinistryTypeId;
    }

    public void setStudentMinistryTypeId(Integer studentMinistryTypeId) {
        this.studentMinistryTypeId = studentMinistryTypeId;
    }

    public Integer getStudentStudyTypeId() {
        return studentStudyTypeId;
    }

    public void setStudentStudyTypeId(Integer studentStudyTypeId) {
        this.studentStudyTypeId = studentStudyTypeId;
    }

    public Integer getStudentUniversityTypeId() {
        return studentUniversityTypeId;
    }

    public void setStudentUniversityTypeId(Integer studentUniversityTypeId) {
        this.studentUniversityTypeId = studentUniversityTypeId;
    }

    public Integer getStudentAddedQuarter() {
        return studentAddedQuarter;
    }

    public void setStudentAddedQuarter(Integer studentAddedQuarter) {
        this.studentAddedQuarter = studentAddedQuarter;
    }

    public Integer getStudentLastQuarter() {
        return studentLastQuarter;
    }

    public void setStudentLastQuarter(Integer studentLastQuarter) {
        this.studentLastQuarter = studentLastQuarter;
    }

    public String getCurrentQuarter() {
        return currentQuarter;
    }

    public void setCurrentQuarter(String currentQuarter) {
        this.currentQuarter = currentQuarter;
    }

    public String getStudentAge() {
        return studentAge;
    }

    public void setStudentAge(String studentAge) {
        this.studentAge = studentAge;
    }

    public Integer getStudyUniversityIn() {
        return studyUniversityIn;
    }

    public void setStudyUniversityIn(Integer studyUniversityIn) {
        this.studyUniversityIn = studyUniversityIn;
    }

    public Integer getStudyCollegeIn() {
        return studyCollegeIn;
    }

    public void setStudyCollegeIn(Integer studyCollegeIn) {
        this.studyCollegeIn = studyCollegeIn;
    }

    public Integer getStudyCountryIn() {
        return studyCountryIn;
    }

    public void setStudyCountryIn(Integer studyCountryIn) {
        this.studyCountryIn = studyCountryIn;
    }

    public Integer getSyudyGovIn() {
        return syudyGovIn;
    }

    public void setSyudyGovIn(Integer syudyGovIn) {
        this.syudyGovIn = syudyGovIn;
    }

    public Integer getStudyEducationIn() {
        return studyEducationIn;
    }

    public void setStudyEducationIn(Integer studyEducationIn) {
        this.studyEducationIn = studyEducationIn;
    }

    public Integer getStudyMajorIn() {
        return studyMajorIn;
    }

    public void setStudyMajorIn(Integer studyMajorIn) {
        this.studyMajorIn = studyMajorIn;
    }

    public Instant getStudentBirthdate() {
        return studentBirthdate;
    }

    public void setStudentBirthdate(Instant studentBirthdate) {
        this.studentBirthdate = studentBirthdate;
    }

    public String getStudentBirthdatePlace() {
        return studentBirthdatePlace;
    }

    public void setStudentBirthdatePlace(String studentBirthdatePlace) {
        this.studentBirthdatePlace = studentBirthdatePlace;
    }

    public Integer getStudentGender() {
        return studentGender;
    }

    public void setStudentGender(Integer studentGender) {
        this.studentGender = studentGender;
    }

    public String getStudentPassportNumber() {
        return studentPassportNumber;
    }

    public void setStudentPassportNumber(String studentPassportNumber) {
        this.studentPassportNumber = studentPassportNumber;
    }

    public Instant getStudentPassportIssueDate() {
        return studentPassportIssueDate;
    }

    public void setStudentPassportIssueDate(Instant studentPassportIssueDate) {
        this.studentPassportIssueDate = studentPassportIssueDate;
    }

    public Instant getStudentPassportExpireDate() {
        return studentPassportExpireDate;
    }

    public void setStudentPassportExpireDate(Instant studentPassportExpireDate) {
        this.studentPassportExpireDate = studentPassportExpireDate;
    }

    public Integer getStudentPassportIssueCountryId() {
        return studentPassportIssueCountryId;
    }

    public void setStudentPassportIssueCountryId(Integer studentPassportIssueCountryId) {
        this.studentPassportIssueCountryId = studentPassportIssueCountryId;
    }

    public Integer getStudyUniversityFeeId() {
        return studyUniversityFeeId;
    }

    public void setStudyUniversityFeeId(Integer studyUniversityFeeId) {
        this.studyUniversityFeeId = studyUniversityFeeId;
    }

    public Integer getStudyUniversityFeeStatusId() {
        return studyUniversityFeeStatusId;
    }

    public void setStudyUniversityFeeStatusId(Integer studyUniversityFeeStatusId) {
        this.studyUniversityFeeStatusId = studyUniversityFeeStatusId;
    }

    public Instant getStudentMidate() {
        return studentMidate;
    }

    public void setStudentMidate(Instant studentMidate) {
        this.studentMidate = studentMidate;
    }

      public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    public String getStudentCellphone() {
        return studentCellphone;
    }

    public void setStudentCellphone(String studentCellphone) {
        this.studentCellphone = studentCellphone;
    }

    public String getStudentHomeNumber() {
        return studentHomeNumber;
    }

    public void setStudentHomeNumber(String studentHomeNumber) {
        this.studentHomeNumber = studentHomeNumber;
    }

    public String getStudentEmergencyContact() {
        return studentEmergencyContact;
    }

    public void setStudentEmergencyContact(String studentEmergencyContact) {
        this.studentEmergencyContact = studentEmergencyContact;
    }

    public String getRecordVisibility() {
        return recordVisibility;
    }

    public void setRecordVisibility(String recordVisibility) {
        this.recordVisibility = recordVisibility != null ? recordVisibility : "ALL";
    }

    public String getCreatedRoleLevel() {
        return createdRoleLevel;
    }

    public void setCreatedRoleLevel(String createdRoleLevel) {
        this.createdRoleLevel = createdRoleLevel;
    }

// to string
  @Override
  public String toString() {
      return "StudentDatum{" +
              ", studentId=" + studentId +
              ", studentName='" + studentName + '\'' +
           //   ", studentCountryId=" + studentCountryId +
              ", studentGovId=" + studentGovId +
              ", studentMudId=" + studentMudId +
              ", studentDelegationDecisionYear='" + studentDelegationDecisionYear + '\'' +
              ", studentDegreeId=" + studentDegreeId +
              ", studentMajorId=" + studentMajorId +
              ", studentMajorName='" + studentMajorName + '\'' +
              ", studentDelegateDecisionNo=" + studentDelegateDecisionNo +
              ", studentStudyNoOfYears=" + studentStudyNoOfYears +
              ", studentCurrentLevelId=" + studentCurrentLevelId +
              ", studentFirstReceiptOfDue=" + studentFirstReceiptOfDue +
              ", studentAmount=" + studentAmount +
              ", studentNote1='" + studentNote1 + '\'' +
              ", studentNote2='" + studentNote2 + '\'' +
              ", studentStudyStatusId=" + studentStudyStatusId +
              ", studentAgencyTypeId=" + studentAgencyTypeId +
              ", studentMinistryTypeId=" + studentMinistryTypeId +
              ", studentStudyTypeId=" + studentStudyTypeId +
              ", studentUniversityTypeId=" + studentUniversityTypeId +
              ", studentAddedQuarter=" + studentAddedQuarter +
              ", studentLastQuarter=" + studentLastQuarter +
              ", studyUniversityIn=" + studyUniversityIn +
              ", studyCollegeIn=" + studyCollegeIn +
              ", studyCountryIn=" + studyCountryIn +
              ", syudyGovIn=" + syudyGovIn +
              ", studyEducationIn=" + studyEducationIn +
              ", studyMajorIn=" + studyMajorIn +
              ", studentBirthdate=" + studentBirthdate +
              ", studentBirthdatePlace='" + studentBirthdatePlace + '\'' +
              ", studentGender=" + studentGender +
              ", studentPassportNumber='" + studentPassportNumber + '\'' +
              ", studentPassportIssueDate=" + studentPassportIssueDate +
              ", studentPassportExpireDate=" + studentPassportExpireDate +
              ", studentPassportIssueCountryId=" + studentPassportIssueCountryId +
              ", studyUniversityFeeId=" + studyUniversityFeeId +
              ", studyUniversityFeeStatusId=" + studyUniversityFeeStatusId +
              ", studentMidate=" + studentMidate +
              '}';
  }

  // hashcode

//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//        StudentDatum that = (StudentDatum) o;
//        return Objects.equals(studentId, that.studentId) && Objects.equals(studentName, that.studentName) && Objects.equals(studentCountryId, that.studentCountryId) && Objects.equals(studentGovId, that.studentGovId) && Objects.equals(studentMudId, that.studentMudId) && Objects.equals(studentDelegationDecisionYear, that.studentDelegationDecisionYear) && Objects.equals(studentDegreeId, that.studentDegreeId) && Objects.equals(studentMajorId, that.studentMajorId) && Objects.equals(studentMajorName, that.studentMajorName) && Objects.equals(studentDelegateDecisionNo, that.studentDelegateDecisionNo) && Objects.equals(studentStudyNoOfYears, that.studentStudyNoOfYears) && Objects.equals(studentCurrentLevelId, that.studentCurrentLevelId) && Objects.equals(studentFirstReceiptOfDue, that.studentFirstReceiptOfDue) && Objects.equals(studentAmount, that.studentAmount) && Objects.equals(studentNote1, that.studentNote1) && Objects.equals(studentNote2, that.studentNote2) && Objects.equals(studentStudyStatusId, that.studentStudyStatusId) && Objects.equals(studentAgencyTypeId, that.studentAgencyTypeId) && Objects.equals(studentMinistryTypeId, that.studentMinistryTypeId) && Objects.equals(studentStudyTypeId, that.studentStudyTypeId) && Objects.equals(studentUniversityTypeId, that.studentUniversityTypeId) && Objects.equals(studentAddedQuarter, that.studentAddedQuarter) && Objects.equals(studentLastQuarter, that.studentLastQuarter) && Objects.equals(studyUniversityIn, that.studyUniversityIn) && Objects.equals(studyCollegeIn, that.studyCollegeIn) && Objects.equals(studyCountryIn, that.studyCountryIn) && Objects.equals(syudyGovIn, that.syudyGovIn) && Objects.equals(studyEducationIn, that.studyEducationIn) && Objects.equals(studyMajorIn, that.studyMajorIn) && Objects.equals(studentBirthdate, that.studentBirthdate) && Objects.equals(studentBirthdatePlace, that.studentBirthdatePlace) && Objects.equals(studentGender, that.studentGender) && Objects.equals(studentPassportNumber, that.studentPassportNumber) && Objects.equals(studentPassportIssueDate, that.studentPassportIssueDate) && Objects.equals(studentPassportExpireDate, that.studentPassportExpireDate) && Objects.equals(studentPassportIssueCountryId, that.studentPassportIssueCountryId) && Objects.equals(studyUniversityFeeId, that.studyUniversityFeeId) && Objects.equals(studyUniversityFeeStatusId, that.studyUniversityFeeStatusId) && Objects.equals(studentMidate, that.studentMidate);
//    }
//
//    @Override
//    public int hashCode() {
//        return Objects.hash(studentId, studentName, studentCountryId, studentGovId, studentMudId, studentDelegationDecisionYear, studentDegreeId, studentMajorId, studentMajorName, studentDelegateDecisionNo, studentStudyNoOfYears, studentCurrentLevelId, studentFirstReceiptOfDue, studentAmount, studentNote1, studentNote2, studentStudyStatusId, studentAgencyTypeId, studentMinistryTypeId, studentStudyTypeId, studentUniversityTypeId, studentAddedQuarter, studentLastQuarter, studyUniversityIn, studyCollegeIn, studyCountryIn, syudyGovIn, studyEducationIn, studyMajorIn, studentBirthdate, studentBirthdatePlace, studentGender, studentPassportNumber, studentPassportIssueDate, studentPassportExpireDate, studentPassportIssueCountryId, studyUniversityFeeId, studyUniversityFeeStatusId, studentMidate);
//    }
}