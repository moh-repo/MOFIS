package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.studentportal.model.StudentChangeRequest;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Enriches {@link StudentChangeRequest} with student name and current study details
 * so the admin review UI can show context without a second round-trip.
 */
@Getter
@Setter
public class StudentChangeRequestDto {

    // ── Core change-request fields ───────────────────────────────────────────
    private Integer requestId;
    private Integer studentId;
    private Integer studentUserId;
    private String  submissionId;
    private String  fieldName;
    private String  fieldLabel;
    private String  oldValue;
    private String  newValue;
    private String  status;
    private String  rejectionReason;
    private String  employeeComment;
    private String  appliedValue;
    /** CHANGE_REQUEST_FIELD.DATA_TYPE (V115), so the review screen can offer a date picker. */
    private String  fieldType;
    /**
     * The old, new and applied values were withheld because the caller may not see this
     * field on the student record either (QA N3, decision DEC-05). The three values are
     * null; the screen says they are hidden rather than empty.
     */
    private boolean valuesMasked;
    private Long    reviewedBy;
    private String  reviewedByName;
    private Instant reviewedAt;
    private Instant appliedAt;
    private Instant createdAt;

    // ── Student / study context (populated from MOHECORE.STUDENT_DATA) ───────
    private String  studentName;
    private Integer studentDegreeId;
    private Integer studentCurrentLevelId;
    private Integer studentStudyStatusId;
    private Integer studyCountryIn;
    private Integer syudyGovIn;
    private Integer studyUniversityIn;
    private Integer studyCollegeIn;
    private Integer studyEducationIn;
    private Integer studyMajorIn;

    // ── Factory ──────────────────────────────────────────────────────────────

    public static StudentChangeRequestDto from(StudentChangeRequest req, StudentDatum student) {
        StudentChangeRequestDto dto = new StudentChangeRequestDto();

        dto.requestId       = req.getRequestId();
        dto.studentId       = req.getStudentId();
        dto.studentUserId   = req.getStudentUserId();
        dto.submissionId    = req.getSubmissionId();
        dto.fieldName       = req.getFieldName();
        dto.fieldLabel      = req.getFieldLabel();
        dto.oldValue        = req.getOldValue();
        dto.newValue        = req.getNewValue();
        dto.status          = req.getStatus();
        dto.rejectionReason = req.getRejectionReason();
        dto.employeeComment = req.getEmployeeComment();
        dto.appliedValue    = req.getAppliedValue();
        dto.reviewedBy      = req.getReviewedBy();
        dto.reviewedAt      = req.getReviewedAt();
        dto.appliedAt       = req.getAppliedAt();
        dto.createdAt       = req.getCreatedAt();

        if (student != null) {
            dto.studentName          = student.getStudentName();
            dto.studentDegreeId      = student.getStudentDegreeId();
            dto.studentCurrentLevelId = student.getStudentCurrentLevelId();
            dto.studentStudyStatusId = student.getStudentStudyStatusId();
            dto.studyCountryIn       = student.getStudyCountryIn();
            dto.syudyGovIn           = student.getSyudyGovIn();
            dto.studyUniversityIn    = student.getStudyUniversityIn();
            dto.studyCollegeIn       = student.getStudyCollegeIn();
            dto.studyEducationIn     = student.getStudyEducationIn();
            dto.studyMajorIn         = student.getStudyMajorIn();
        }

        return dto;
    }
}
