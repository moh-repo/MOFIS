package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentExtensionDto {
    private Integer extensionId;
    private Integer studentId;
    private String studentName;
    private String extensionType;
    /** Localized display label for {@link #extensionType} in the caller's language. */
    private String extensionTypeLabel;
    private Integer extensionQuarters;
    private LocalDate extensionStartDate;
    private LocalDate extensionEndDate;
    private String extensionReason;
    private String extensionReasonDetails;
    private String extensionDecisionNo;
    private LocalDate extensionDecisionDate;
    private String approvedBy;
    private String extensionStatus;
    /** Localized display label for {@link #extensionStatus} in the caller's language. */
    private String extensionStatusLabel;
    private LocalDateTime createdDate;
    private String createdBy;
    private LocalDateTime modifiedDate;
    private String modifiedBy;
    private String notes;
}


