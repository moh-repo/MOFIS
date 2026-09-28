package com.dilaw.mohe.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class QuarterlyFinancialReportDto {
    
    // Report metadata.
    //
    // quarter/year are the period the figures below actually come from. When the
    // requested period holds no finance rows the service falls back to the most
    // recent period that does, so these can differ from requestedQuarter/
    // requestedYear — periodSubstituted says whether that happened. Callers must
    // label the report from quarter/year, never from what the user asked for.
    private String reportDate;
    private String reportNumber;
    private Integer quarter;
    private Integer year;
    private String quarterName;
    private Integer requestedQuarter;
    private Integer requestedYear;
    private boolean periodSubstituted;

    // Entity breakdown
    private List<EntityFinancialSummary> entities;
    private Integer entityCount;

    // Totals
    private Long totalReceivedAmount;
    private Integer totalReceivedCount;
    private Long totalDisbursedAmount;
    private Integer totalDisbursedCount;
    private Long totalRemainingAmount;
    private Integer totalRemainingCount;
    
    // Calculated metrics
    private Double disbursementRatio; // Percentage of disbursed vs received
    
    @Getter
    @Setter
    public static class EntityFinancialSummary {
        private String entityName;
        /** The same name in English; resolved by BilingualRows for an EN render (QA B6). */
        private String entityNameEn;
        private Long receivedAmount;
        private Integer receivedCount;
        private Long disbursedAmount;
        private Integer disbursedCount;
        private Long remainingAmount;
        private Integer remainingCount;
    }
}

