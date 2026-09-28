package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One row of the Growth Analysis PDF / Excel (FIX-PLAN P5-10, 26 Sep 2026).
 *
 * <p>The export handed the view's rows to the template, which printed the stored codes:
 * {@code total_students} as the category and {@code growing} / {@code declining} /
 * {@code stable} as the status. The codes stay here for the filters; the labels are what
 * the template prints, in the requested language.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrowthAnalysisReportRow {
    private String growthCategory;
    private String growthCategoryLabel;
    private Integer analysisYear;
    private BigDecimal growthRate;
    private String growthStatus;
    private String growthStatusLabel;
}
