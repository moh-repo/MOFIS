package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One attaché, summarised.
 *
 * <p>The readout of the country-scope work: for each attaché, how many students
 * it holds, what they are owed, and what reference data it has of its own.
 *
 * <p><b>Worth knowing before reading it:</b> every student in this database is
 * currently in Egypt, so today this report has one meaningful row and the rest
 * read zero. That is accurate, not broken — the zeroes are the point, because
 * they show which attachés have no banks, no branches and no rules of their own
 * yet.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttacheSummaryRowDto {

    private Integer studyCountryInId;
    private String studyCountryName;

    private long studentCount;
    private long activeStudentCount;

    private long financeRecordCount;
    private BigDecimal financeTotal;

    private long bankCount;
    private long bankBranchCount;

    /** Study-years and study-amount rules this attaché has set of its own. */
    private long studyYearsOverrides;
    private long studyAmountOverrides;

    private long staffCount;
}
