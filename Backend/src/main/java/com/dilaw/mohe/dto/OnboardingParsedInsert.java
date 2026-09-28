package com.dilaw.mohe.dto;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;

import java.util.List;

public record OnboardingParsedInsert(
        StudentDatum studentData,
        StudentFinance linkedFinanceDraft,
        String pendingUploadSessionId,
        List<StudentParentDto> parents,
        List<StudentRelativeDto> relatives) {

    public OnboardingParsedInsert(StudentDatum studentData, StudentFinance linkedFinanceDraft,
                                  String pendingUploadSessionId) {
        this(studentData, linkedFinanceDraft, pendingUploadSessionId, null, null);
    }
}
