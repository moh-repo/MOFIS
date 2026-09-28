package com.dilaw.mohe.dto;

import com.dilaw.mohe.config.FieldPermission;
import com.dilaw.mohe.config.SensitiveField;
import com.dilaw.mohe.model.StudentDatum;
import org.springframework.beans.BeanUtils;

import java.time.Instant;

/**
 * One student as the edit wizard needs it (live QA H3, 23 Sep 2026).
 *
 * <p>{@code GET /api/v1/studentdata/{id}} answered with {@link StudentDatumDto}, the
 * 27-field list summary. The wizard loaded that, showed its ten steps with the other
 * fields blank, and submitted its whole model: nine columns the form never showed —
 * agency, ministry, study and university type, birthplace, the passport dates and
 * issuing country, the amount currency — reached the approver as "cleared" and were
 * written as NULL or 0 once approved. This subclass carries every scalar the wizard
 * binds, so an edit round-trips what it does not touch.
 *
 * <p>Kept apart from the list DTO on purpose: the list endpoints hand that class out
 * in bulk, and the notes, contact details and picture path have no business in a list
 * row. The masking annotations are copied from {@link StudentDatum} verbatim because
 * {@code SensitiveFieldModule} reads them from the class it serialises, not from the
 * entity; {@code StudentDatumDetailDtoTest} holds that every masked column stays
 * masked here. Field types are the entity's own, or {@code BeanUtils.copyProperties}
 * skips them without a word.
 */
public class StudentDatumDetailDto extends StudentDatumDto {

    @SensitiveField(anyOf = {"FINANCE.VIEW", "FINANCE.EDIT", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    private Integer studentFirstReceiptOfDue;
    @SensitiveField(anyOf = {"FINANCE.VIEW", "FINANCE.EDIT", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    private Integer amountCurrency;
    private String studentNote1;
    private String studentNote2;
    private Integer studentAgencyTypeId;
    private Integer studentMinistryTypeId;
    private Integer studentStudyTypeId;
    private Integer studentUniversityTypeId;
    @SensitiveField(anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    private String studentBirthdatePlace;
    @SensitiveField(anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    private Instant studentPassportIssueDate;
    @SensitiveField(anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    private Instant studentPassportExpireDate;
    private Integer studentPassportIssueCountryId;
    private Integer studyUniversityFeeId;
    private Integer studyUniversityFeeStatusId;
    private Integer universityFeeStatusCurrency;
    private String picturePath;
    @FieldPermission(page = "STUDENTS_LIST", field = "STUDENT_EMAIL", anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    private String studentEmail;
    @FieldPermission(page = "STUDENTS_LIST", field = "STUDENT_PHONE", anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    private String studentCellphone;
    @SensitiveField(anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    private String studentHomeNumber;
    @FieldPermission(page = "STUDENTS_ADD", field = "ADD_STUDENT_EMERGENCY_CONTACT", anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW", "UMS.VIEW"})
    private String studentEmergencyContact;

    public static StudentDatumDetailDto fromEntity(StudentDatum entity) {
        if (entity == null) {
            return null;
        }
        StudentDatumDetailDto dto = new StudentDatumDetailDto();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    public Integer getStudentFirstReceiptOfDue() { return studentFirstReceiptOfDue; }
    public void setStudentFirstReceiptOfDue(Integer studentFirstReceiptOfDue) { this.studentFirstReceiptOfDue = studentFirstReceiptOfDue; }
    public Integer getAmountCurrency() { return amountCurrency; }
    public void setAmountCurrency(Integer amountCurrency) { this.amountCurrency = amountCurrency; }
    public String getStudentNote1() { return studentNote1; }
    public void setStudentNote1(String studentNote1) { this.studentNote1 = studentNote1; }
    public String getStudentNote2() { return studentNote2; }
    public void setStudentNote2(String studentNote2) { this.studentNote2 = studentNote2; }
    public Integer getStudentAgencyTypeId() { return studentAgencyTypeId; }
    public void setStudentAgencyTypeId(Integer studentAgencyTypeId) { this.studentAgencyTypeId = studentAgencyTypeId; }
    public Integer getStudentMinistryTypeId() { return studentMinistryTypeId; }
    public void setStudentMinistryTypeId(Integer studentMinistryTypeId) { this.studentMinistryTypeId = studentMinistryTypeId; }
    public Integer getStudentStudyTypeId() { return studentStudyTypeId; }
    public void setStudentStudyTypeId(Integer studentStudyTypeId) { this.studentStudyTypeId = studentStudyTypeId; }
    public Integer getStudentUniversityTypeId() { return studentUniversityTypeId; }
    public void setStudentUniversityTypeId(Integer studentUniversityTypeId) { this.studentUniversityTypeId = studentUniversityTypeId; }
    public String getStudentBirthdatePlace() { return studentBirthdatePlace; }
    public void setStudentBirthdatePlace(String studentBirthdatePlace) { this.studentBirthdatePlace = studentBirthdatePlace; }
    public Instant getStudentPassportIssueDate() { return studentPassportIssueDate; }
    public void setStudentPassportIssueDate(Instant studentPassportIssueDate) { this.studentPassportIssueDate = studentPassportIssueDate; }
    public Instant getStudentPassportExpireDate() { return studentPassportExpireDate; }
    public void setStudentPassportExpireDate(Instant studentPassportExpireDate) { this.studentPassportExpireDate = studentPassportExpireDate; }
    public Integer getStudentPassportIssueCountryId() { return studentPassportIssueCountryId; }
    public void setStudentPassportIssueCountryId(Integer studentPassportIssueCountryId) { this.studentPassportIssueCountryId = studentPassportIssueCountryId; }
    public Integer getStudyUniversityFeeId() { return studyUniversityFeeId; }
    public void setStudyUniversityFeeId(Integer studyUniversityFeeId) { this.studyUniversityFeeId = studyUniversityFeeId; }
    public Integer getStudyUniversityFeeStatusId() { return studyUniversityFeeStatusId; }
    public void setStudyUniversityFeeStatusId(Integer studyUniversityFeeStatusId) { this.studyUniversityFeeStatusId = studyUniversityFeeStatusId; }
    public Integer getUniversityFeeStatusCurrency() { return universityFeeStatusCurrency; }
    public void setUniversityFeeStatusCurrency(Integer universityFeeStatusCurrency) { this.universityFeeStatusCurrency = universityFeeStatusCurrency; }
    public String getPicturePath() { return picturePath; }
    public void setPicturePath(String picturePath) { this.picturePath = picturePath; }
    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }
    public String getStudentCellphone() { return studentCellphone; }
    public void setStudentCellphone(String studentCellphone) { this.studentCellphone = studentCellphone; }
    public String getStudentHomeNumber() { return studentHomeNumber; }
    public void setStudentHomeNumber(String studentHomeNumber) { this.studentHomeNumber = studentHomeNumber; }
    public String getStudentEmergencyContact() { return studentEmergencyContact; }
    public void setStudentEmergencyContact(String studentEmergencyContact) { this.studentEmergencyContact = studentEmergencyContact; }
}
