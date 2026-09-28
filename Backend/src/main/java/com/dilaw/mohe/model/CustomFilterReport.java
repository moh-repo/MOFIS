package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row per student for the custom filter report.
 *
 * <p>Mapped as a subselect rather than the {@code V_Custom_Filter_Report} view it
 * used to name: the view's columns no longer matched the entity, so anything but
 * the repository's native queries failed with "Invalid column name". The subselect
 * is the native SELECT without its filter parameters (the repository still runs
 * those); V066 drops the view. See {@code CustomFilterReportRepository} for why the
 * three date columns are NULL.
 *
 * <p>Each lookup name has an {@code _en} sibling, with the V104 fallback to the
 * Arabic, so {@code BilingualRows} can give an English PDF or Excel English names
 * (QA B6 / live QA #12). The repository's native queries select them too.
 */
@Getter
@Setter
@Entity
@Immutable
@Subselect("""
    SELECT
        CAST(sd.STUDENT_ID AS BIGINT) AS student_id,
        sd.STUDENT_NAME AS student_name,
        sd.STUDENT_PASSPORT_NUMBER AS national_number,
        sui.STUDY_UNIVERSITY_IN_NAME AS university_name,
        COALESCE(NULLIF(sui.STUDY_UNIVERSITY_IN_NAME_EN, N''), sui.STUDY_UNIVERSITY_IN_NAME) AS university_name_en,
        sm.STUDENT_MAJOR_NAME AS major_name,
        COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME) AS major_name_en,
        sci.STUDY_COUNTRY_IN_NAME AS country_name,
        COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME) AS country_name_en,
        sd.STUDY_COUNTRY_IN AS student_country_id,
        sd.STUDENT_DEGREE_ID AS degree_level_id,
        sdeg.STUDENT_DEGREE_NAME AS degree_level_name,
        COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME) AS degree_level_name_en,
        sd.STUDENT_STUDY_STATUS_ID AS study_status_id,
        sss.STUDENT_STUDY_STATUS_NAME AS study_status_name,
        COALESCE(NULLIF(sss.STUDENT_STUDY_STATUS_NAME_EN, N''), sss.STUDENT_STUDY_STATUS_NAME) AS study_status_name_en,
        sd.STUDENT_STUDY_TYPE_ID AS study_type_id,
        sst.STUDENT_STUDY_TYPE_NAME AS study_type_name,
        COALESCE(NULLIF(sst.STUDENT_STUDY_TYPE_NAME_EN, N''), sst.STUDENT_STUDY_TYPE_NAME) AS study_type_name_en,
        sd.STUDENT_GENDER AS gender_id,
        CAST(NULL AS DATE) AS enrollment_date,
        CAST(NULL AS DATE) AS study_start_date,
        CAST(NULL AS DATE) AS study_end_date,
        CAST(ISNULL(fin.total_amount, 0) AS DECIMAL(18,2)) AS financial_amount,
        sd.STUDENT_STUDY_NO_OF_YEARS AS study_years,
        sd.STUDENT_DELEGATION_DECISION_YEAR AS enrollment_year,
        CASE
            WHEN sss.CODE = 'ACTIVE'      THEN 'active'
            WHEN sss.CODE = 'NEW'         THEN 'new'
            WHEN sss.CODE = 'SUSPENDED'   THEN 'suspended'
            WHEN sss.CODE = 'WITHDRAWN'   THEN 'withdrawn'
            WHEN sss.CODE = 'TRANSFERRED' THEN 'transferred'
            ELSE 'other'
        END AS status_category
    FROM MOHECORE.STUDENT_DATA sd
    LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN sui ON sui.STUDY_UNIVERSITY_IN_ID = sd.STUDY_UNIVERSITY_IN
    LEFT JOIN MOHECORE.STUDENT_MAJOR sm ON sm.STUDENT_MAJOR_ID = sd.STUDENT_MAJOR_ID
    LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
    LEFT JOIN MOHECORE.STUDENT_DEGREE sdeg ON sdeg.STUDENT_DEGREE_ID = sd.STUDENT_DEGREE_ID
    LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
    LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE sst ON sst.STUDENT_STUDY_TYPE_ID = sd.STUDENT_STUDY_TYPE_ID
    LEFT JOIN (
        SELECT STUDENT_ID, SUM(NET_AMOUNT) AS total_amount
        FROM MOHECORE.STUDENT_FINANCE
        GROUP BY STUDENT_ID
    ) fin ON fin.STUDENT_ID = sd.STUDENT_ID
    WHERE ISNULL(sd.IS_DELETED, 0) = 0
""")
@Synchronize({
        "STUDENT_DATA", "STUDY_UNIVERSITY_IN", "STUDENT_MAJOR", "STUDY_COUNTRY_IN",
        "STUDENT_DEGREE", "STUDENT_STUDY_STATUS", "STUDENT_STUDY_TYPE", "STUDENT_FINANCE"
})
public class CustomFilterReport {

    @Id
    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "student_name")
    private String studentName;

    /** The student's national id under another name; masked by its rule (W5-03). */
    @com.dilaw.mohe.config.MaskedAs(type = com.dilaw.mohe.model.StudentDatum.class, field = "studentGovId")
    @Column(name = "national_number")
    private String nationalNumber;

    @Column(name = "university_name")
    private String universityName;

    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "university_name_en")
    private String universityNameEn;

    @Column(name = "major_name")
    private String majorName;

    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "major_name_en")
    private String majorNameEn;

    @Column(name = "country_name")
    private String countryName;

    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "country_name_en")
    private String countryNameEn;

    @Column(name = "student_country_id")
    private Integer studentCountryId;

    @Column(name = "degree_level_id")
    private Integer degreeLevelId;

    @Column(name = "degree_level_name")
    private String degreeLevelName;

    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "degree_level_name_en")
    private String degreeLevelNameEn;

    @Column(name = "study_status_id")
    private Integer studyStatusId;

    @Column(name = "study_status_name")
    private String studyStatusName;

    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "study_status_name_en")
    private String studyStatusNameEn;

    @Column(name = "study_type_id")
    private Integer studyTypeId;

    @Column(name = "study_type_name")
    private String studyTypeName;

    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "study_type_name_en")
    private String studyTypeNameEn;

    @Column(name = "gender_id")
    private Integer genderId;

    @Column(name = "enrollment_date")
    private LocalDate enrollmentDate;

    @Column(name = "study_start_date")
    private LocalDate studyStartDate;

    @Column(name = "study_end_date")
    private LocalDate studyEndDate;

    /** The student's scholarship amount under another name; masked by its rule (W5-03). */
    @com.dilaw.mohe.config.MaskedAs(type = com.dilaw.mohe.model.StudentDatum.class, field = "studentAmount")
    @Column(name = "financial_amount")
    private BigDecimal financialAmount;

    @Column(name = "study_years")
    private Integer studyYears;

    @Column(name = "enrollment_year")
    private Integer enrollmentYear;

    @Column(name = "status_category")
    private String statusCategory;
}
