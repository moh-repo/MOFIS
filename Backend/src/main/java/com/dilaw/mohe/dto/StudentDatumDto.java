package com.dilaw.mohe.dto;

import com.dilaw.mohe.config.FieldPermission;
import com.dilaw.mohe.config.SensitiveField;
import com.dilaw.mohe.model.StudentDatum;
import org.springframework.beans.BeanUtils;

import java.time.Instant;
import org.springframework.data.domain.Page;

/**
 * API response for student list/search — scalar fields only, no JPA relations.
 *
 * <p>The field types are the entity's own. {@code BeanUtils.copyProperties} copies a
 * property only when the types agree, and this class declared {@code studentAmount}
 * as {@code Double} and {@code studentBirthdate} as {@code LocalDate} against the
 * entity's {@code Integer} and {@code Instant}: both were null in every list, silently.
 *
 * <p>The masking annotations are the entity's too (R-18 / O-03). Without them this
 * list handed the passport and national id numbers to readers the student screens
 * mask them for — the same gap as QA N3 on Change Requests.
 */
public class StudentDatumDto {

    private Integer studentId;
    private String studentName;
    private Integer studentCountryId;
    @FieldPermission(page = "STUDENTS_ADD", field = "ADD_STUDENT_ID_NUMBER", anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    private Integer studentGovId;
    private Integer studentMudId;
    private String studentDelegationDecisionYear;
    private Integer studentDegreeId;
    private Integer studentMajorId;
    private String studentMajorName;
    private Integer studentDelegateDecisionNo;
    private Integer studentStudyNoOfYears;
    private Integer studentCurrentLevelId;
    private Integer studentStudyStatusId;
    private Integer studentGender;
    @FieldPermission(page = "STUDENTS_ADD", field = "ADD_STUDENT_PASSPORT", anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    private String studentPassportNumber;
    @FieldPermission(page = "STUDENTS_LIST", field = "STUDENT_BIRTHDATE", anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    private Instant studentBirthdate;
    private Integer studyCountryIn;
    private Integer syudyGovIn;
    private Integer studyUniversityIn;
    private Integer studyCollegeIn;
    private Integer studyEducationIn;
    private Integer studyMajorIn;
    @SensitiveField(anyOf = {"FINANCE.VIEW", "FINANCE.EDIT", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    private Integer studentAmount;
    private Integer studentAddedQuarter;
    private Integer studentLastQuarter;
    private String currentQuarter;
    /** When the student was added: the dashboard's "Added Date" (QA DSH-001), empty until now. */
    private Instant studentMidate;

    public static StudentDatumDto fromEntity(StudentDatum entity) {
        if (entity == null) {
            return null;
        }
        StudentDatumDto dto = new StudentDatumDto();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    public static Page<StudentDatumDto> fromPage(Page<StudentDatum> page) {
        return page.map(StudentDatumDto::fromEntity);
    }

    public Integer getStudentId() { return studentId; }
    public void setStudentId(Integer studentId) { this.studentId = studentId; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public Integer getStudentCountryId() { return studentCountryId; }
    public void setStudentCountryId(Integer studentCountryId) { this.studentCountryId = studentCountryId; }
    public Integer getStudentGovId() { return studentGovId; }
    public void setStudentGovId(Integer studentGovId) { this.studentGovId = studentGovId; }
    public Integer getStudentMudId() { return studentMudId; }
    public void setStudentMudId(Integer studentMudId) { this.studentMudId = studentMudId; }
    public String getStudentDelegationDecisionYear() { return studentDelegationDecisionYear; }
    public void setStudentDelegationDecisionYear(String studentDelegationDecisionYear) { this.studentDelegationDecisionYear = studentDelegationDecisionYear; }
    public Integer getStudentDegreeId() { return studentDegreeId; }
    public void setStudentDegreeId(Integer studentDegreeId) { this.studentDegreeId = studentDegreeId; }
    public Integer getStudentMajorId() { return studentMajorId; }
    public void setStudentMajorId(Integer studentMajorId) { this.studentMajorId = studentMajorId; }
    public String getStudentMajorName() { return studentMajorName; }
    public void setStudentMajorName(String studentMajorName) { this.studentMajorName = studentMajorName; }
    public Integer getStudentDelegateDecisionNo() { return studentDelegateDecisionNo; }
    public void setStudentDelegateDecisionNo(Integer studentDelegateDecisionNo) { this.studentDelegateDecisionNo = studentDelegateDecisionNo; }
    public Integer getStudentStudyNoOfYears() { return studentStudyNoOfYears; }
    public void setStudentStudyNoOfYears(Integer studentStudyNoOfYears) { this.studentStudyNoOfYears = studentStudyNoOfYears; }
    public Integer getStudentCurrentLevelId() { return studentCurrentLevelId; }
    public void setStudentCurrentLevelId(Integer studentCurrentLevelId) { this.studentCurrentLevelId = studentCurrentLevelId; }
    public Integer getStudentStudyStatusId() { return studentStudyStatusId; }
    public void setStudentStudyStatusId(Integer studentStudyStatusId) { this.studentStudyStatusId = studentStudyStatusId; }
    public Integer getStudentGender() { return studentGender; }
    public void setStudentGender(Integer studentGender) { this.studentGender = studentGender; }
    public String getStudentPassportNumber() { return studentPassportNumber; }
    public void setStudentPassportNumber(String studentPassportNumber) { this.studentPassportNumber = studentPassportNumber; }
    public Instant getStudentBirthdate() { return studentBirthdate; }
    public void setStudentBirthdate(Instant studentBirthdate) { this.studentBirthdate = studentBirthdate; }
    public Integer getStudyCountryIn() { return studyCountryIn; }
    public void setStudyCountryIn(Integer studyCountryIn) { this.studyCountryIn = studyCountryIn; }
    public Integer getSyudyGovIn() { return syudyGovIn; }
    public void setSyudyGovIn(Integer syudyGovIn) { this.syudyGovIn = syudyGovIn; }
    public Integer getStudyUniversityIn() { return studyUniversityIn; }
    public void setStudyUniversityIn(Integer studyUniversityIn) { this.studyUniversityIn = studyUniversityIn; }
    public Integer getStudyCollegeIn() { return studyCollegeIn; }
    public void setStudyCollegeIn(Integer studyCollegeIn) { this.studyCollegeIn = studyCollegeIn; }
    public Integer getStudyEducationIn() { return studyEducationIn; }
    public void setStudyEducationIn(Integer studyEducationIn) { this.studyEducationIn = studyEducationIn; }
    public Integer getStudyMajorIn() { return studyMajorIn; }
    public void setStudyMajorIn(Integer studyMajorIn) { this.studyMajorIn = studyMajorIn; }
    public Integer getStudentAmount() { return studentAmount; }
    public void setStudentAmount(Integer studentAmount) { this.studentAmount = studentAmount; }
    public Integer getStudentAddedQuarter() { return studentAddedQuarter; }
    public void setStudentAddedQuarter(Integer studentAddedQuarter) { this.studentAddedQuarter = studentAddedQuarter; }
    public Integer getStudentLastQuarter() { return studentLastQuarter; }
    public void setStudentLastQuarter(Integer studentLastQuarter) { this.studentLastQuarter = studentLastQuarter; }
    public String getCurrentQuarter() { return currentQuarter; }
    public void setCurrentQuarter(String currentQuarter) { this.currentQuarter = currentQuarter; }
    public Instant getStudentMidate() { return studentMidate; }
    public void setStudentMidate(Instant studentMidate) { this.studentMidate = studentMidate; }
}
