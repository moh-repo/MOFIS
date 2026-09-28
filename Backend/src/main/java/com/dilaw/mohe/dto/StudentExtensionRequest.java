package com.dilaw.mohe.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class StudentExtensionRequest {

    private Integer studentId;

    /**
     * Student's name, carried in the approval payload so the pending-approval view can show
     * who the extension is for. Resolved server-side from {@link #studentId}; ignored when the
     * request is executed (the service resolves the record from the DB).
     */
    private String studentName;

    private String extensionType;

    private Integer extensionQuarters;

    private LocalDate extensionStartDate;

    private LocalDate extensionEndDate;

    private String extensionReason;

    private String extensionReasonDetails;

    private String extensionDecisionNo;

    private LocalDate extensionDecisionDate;

    private String approvedBy;

    private String extensionStatus;

    private String createdBy;

    private String modifiedBy;

    private String notes;
}


