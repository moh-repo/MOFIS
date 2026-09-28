package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One approval request on the queue report.
 *
 * <p>Carries {@code ageDays} rather than leaving the reader to subtract dates.
 * The question a pending-approvals report answers is "what has been waiting, and
 * for how long"; making that a column is the difference between a list and a
 * report.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalsReportRowDto {

    private Long id;
    private String requestType;
    private String entityType;
    private String entityId;

    private String status;
    private Integer currentLevel;

    /**
     * The three codes by name, in the report's language (W3-07): the PDF and the
     * workbook printed PENDING, UPDATE and STUDENT_DATA. The codes stay for the
     * screen's own tags and filters.
     */
    private String requestTypeName;
    private String entityTypeName;
    private String statusName;

    private String requestedBy;
    private LocalDateTime requestedAt;

    /** Days since the request was raised — the number the queue is judged on. */
    private long ageDays;

    private Integer countryId;
    private String countryName;

    /** Rejection reason where there is one; blank otherwise. */
    private String note;
}
