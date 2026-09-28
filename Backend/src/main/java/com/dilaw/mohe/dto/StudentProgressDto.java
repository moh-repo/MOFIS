package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Student academic progress: current year and progress percentage.
 *
 * <p>Until V075 this also carried {@code gpa} and {@code graduated} from
 * {@code V_Academic_Performance}; both were NULL / never-true by construction
 * (no grade and no graduation is recorded in this database), so they were
 * removed rather than shown as a dash.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProgressDto {

    private Integer studentId;
    /** Display label e.g. "Year 1" or level name "Level 1" */
    private String currentYearLabel;
    /**
     * The same year in English. The label came from the Arabic column only, so an
     * English profile read "Current Level: Second Year" beside "Current Year:
     * السنة الثانية" (QA B1, STD-007).
     */
    private String currentYearLabelEn;
    /** 1-based year order (e.g. 1, 2, 3) from STUDENT_CURRENT_LEVEL order */
    private Integer currentYearOrder;
    /** Total program years from STUDENT_DATA */
    private Integer totalYears;
    /** 0-100 derived from currentYearOrder / totalYears; 0 if unknown */
    private Integer progressPercent;
}
