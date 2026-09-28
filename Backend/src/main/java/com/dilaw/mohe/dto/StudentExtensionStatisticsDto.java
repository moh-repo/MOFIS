package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bean fields align with student-extension-statistics.jrxml ($F{extensionType}, etc.).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentExtensionStatisticsDto {
    String extensionType;
    Long totalExtensions;
    Long approvedCount;
    Long pendingCount;
    Long rejectedCount;
    Double approvalRate;
    Double averageQuarters;
}
