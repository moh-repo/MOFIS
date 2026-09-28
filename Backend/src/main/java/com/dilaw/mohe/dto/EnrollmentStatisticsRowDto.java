package com.dilaw.mohe.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * One enrollment-statistics row, with the packed quarter code resolved.
 *
 * <p>{@code V_Enrollment_Statistics.enrollment_quarter} does not hold 1..4: it
 * holds the MOHE quarter code, e.g. {@code 20251} for Q1 2025, which is the key
 * of {@code MOHECORE.MOHE_QUARTER}. The view carries no year column at all, so
 * the year and the quarter number are read from that dimension table rather than
 * being derived or left blank.
 *
 * <p>Rows whose code is absent from the dimension (notably the {@code 0} bucket
 * for students with no recorded quarter) keep a null year and quarter number and
 * fall back to a generic label.
 *
 * <p>Each name carries its {@code En} sibling from the view, so {@code BilingualRows}
 * can give an English report English names (QA B6). Before, the DTO dropped them
 * and the English PDF printed Arabic under English headings.
 */
@Getter
@Setter
public class EnrollmentStatisticsRowDto {

    /** Raw MOHE quarter code as stored in the view, e.g. 20251. */
    private Integer quarterCode;
    /** Calendar year from MOHE_QUARTER, e.g. 2025. Null when unresolved. */
    private Integer enrollmentYear;
    /** Quarter within the year, 1..4. Null when unresolved. */
    private Integer enrollmentQuarter;
    /** Display label in the requested language, e.g. "الربع الأول 2025". */
    private String quarterLabel;

    private Integer studentCountryId;
    private String studentCountryName;
    private String studentCountryNameEn;
    private Integer studentMajorId;
    private String studentMajorName;
    private String studentMajorNameEn;
    private Integer studentDegreeId;
    private String studentDegreeName;
    private String studentDegreeNameEn;
    private Integer studentStudyTypeId;
    private String studentStudyTypeName;
    private String studentStudyTypeNameEn;
    private Integer studentCount;
}
