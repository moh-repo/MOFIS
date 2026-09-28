package com.dilaw.mohe.dto;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Wrapped payload for STUDENT_DATA approval INSERT — optional finance draft queued after student EXECUTED.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StudentOnboardingPayload {
    private StudentDatum studentData;
    /** Optional; if null server builds minimal draft from studentData. */
    private StudentFinance linkedFinanceDraft;
    /** Staged uploads reference (disk path key under uploads/pending-onboarding/). */
    private String pendingUploadSessionId;
    /**
     * Parents and relatives typed into the wizard. The student has no id until the
     * request is approved, so they ride along here and are written on execution.
     */
    private List<StudentParentDto> parents;
    private List<StudentRelativeDto> relatives;
}
