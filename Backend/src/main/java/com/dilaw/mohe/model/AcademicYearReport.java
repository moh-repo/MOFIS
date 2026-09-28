package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * One row per (year, study country) for the academic year report.
 *
 * <p>Mapped as a subselect rather than the {@code V_Academic_Year_Report} view it
 * used to name: the view's columns no longer matched the entity, so anything but
 * the repository's native queries failed with "Invalid column name". The subselect
 * is the native SELECT without its filter parameters; V066 drops the view. See
 * {@code AcademicYearReportRepository} for why the graduation columns are NULL.
 */
@Getter
@Setter
@Entity
@Immutable
@Subselect("""
    SELECT
        CAST(YEAR(sf.STUDY_FINANCE_MIDATE) AS NVARCHAR(10)) AS academic_year,
        sd.STUDY_COUNTRY_IN AS study_country_id,
        COUNT(DISTINCT sd.STUDENT_ID) AS total_enrolled_students,
        COUNT(DISTINCT CASE WHEN sss.CODE = 'ACTIVE' THEN sd.STUDENT_ID END) AS continuing_students,
        COUNT(DISTINCT CASE WHEN sss.CODE = 'NEW' THEN sd.STUDENT_ID END) AS new_students,
        COUNT(DISTINCT CASE WHEN sss.CODE = 'SUSPENDED' THEN sd.STUDENT_ID END) AS suspended_students,
        COUNT(DISTINCT CASE WHEN sss.CODE = 'TRANSFERRED' THEN sd.STUDENT_ID END) AS transferred_students,
        CASE WHEN COUNT(DISTINCT sd.STUDENT_ID) = 0 THEN CAST(0 AS DECIMAL(5,2))
             ELSE CAST(COUNT(DISTINCT CASE WHEN sss.CODE = 'ACTIVE' THEN sd.STUDENT_ID END) * 100.0
                  / COUNT(DISTINCT sd.STUDENT_ID) AS DECIMAL(5,2)) END AS retention_rate,
        CAST(AVG(CAST(NULLIF(sd.STUDENT_STUDY_NO_OF_YEARS, 0) AS DECIMAL(9,4))) AS DECIMAL(5,2)) AS avg_study_duration,
        COUNT(DISTINCT CASE WHEN sty.IS_SCHOLARSHIP = 1 THEN sd.STUDENT_ID END) AS scholarship_students,
        COUNT(DISTINCT CASE WHEN sty.IS_SCHOLARSHIP = 0 THEN sd.STUDENT_ID END) AS regular_students,
        COUNT(DISTINCT sd.STUDY_UNIVERSITY_IN) AS total_universities,
        COUNT(DISTINCT sd.STUDY_COUNTRY_IN) AS total_countries,
        COUNT(DISTINCT sd.STUDENT_MAJOR_ID) AS total_majors
    FROM MOHECORE.STUDENT_FINANCE sf
    INNER JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = sf.STUDENT_ID AND ISNULL(sd.IS_DELETED, 0) = 0
    LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
    LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE sty ON sty.STUDENT_STUDY_TYPE_ID = sd.STUDENT_STUDY_TYPE_ID
    WHERE sf.STUDY_FINANCE_MIDATE IS NOT NULL
    GROUP BY sd.STUDY_COUNTRY_IN, YEAR(sf.STUDY_FINANCE_MIDATE)
""")
@Synchronize({"STUDENT_FINANCE", "STUDENT_DATA", "STUDENT_STUDY_STATUS", "STUDENT_STUDY_TYPE"})
@IdClass(AcademicYearReport.AcademicYearReportId.class)
public class AcademicYearReport {

    @Id
    @Column(name = "academic_year")
    private String academicYear;

    @Id
    @Column(name = "study_country_id")
    private Integer countryId;

    @Column(name = "total_enrolled_students")
    private Integer totalEnrolledStudents;

    @Column(name = "continuing_students")
    private Integer continuingStudents;

    @Column(name = "new_students")
    private Integer newStudents;
    @Column(name = "suspended_students")
    private Integer suspendedStudents;

    @Column(name = "transferred_students")
    private Integer transferredStudents;
    @Column(name = "retention_rate")
    private BigDecimal retentionRate;

    @Column(name = "avg_study_duration")
    private BigDecimal avgStudyDuration;

    @Column(name = "scholarship_students")
    private Integer scholarshipStudents;

    @Column(name = "regular_students")
    private Integer regularStudents;
    @Column(name = "total_universities")
    private Integer totalUniversities;

    @Column(name = "total_countries")
    private Integer totalCountries;

    @Column(name = "total_majors")
    private Integer totalMajors;

    @Getter
    @Setter
    @lombok.EqualsAndHashCode
    public static class AcademicYearReportId implements Serializable {
        private String academicYear;
        private Integer countryId;
    }
}
