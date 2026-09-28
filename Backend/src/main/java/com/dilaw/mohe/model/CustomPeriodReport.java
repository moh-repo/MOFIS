package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row per (calendar month, study country) for the custom period report.
 *
 * <p>Mapped as a subselect rather than the {@code V_Custom_Period_Report} view it
 * used to name: the view's columns no longer matched the entity, so anything but
 * the repository's native query failed with "Invalid column name". The subselect
 * is the native SELECT without its filter parameters; V066 drops the view. See
 * {@code CustomPeriodReportRepository} for the column notes.
 */
@Getter
@Setter
@Entity
@Immutable
@Subselect("""
    SELECT
        DATEFROMPARTS(YEAR(sf.STUDY_FINANCE_MIDATE), MONTH(sf.STUDY_FINANCE_MIDATE), 1) AS report_start_date,
        sd.STUDY_COUNTRY_IN AS study_country_id,
        EOMONTH(DATEFROMPARTS(YEAR(sf.STUDY_FINANCE_MIDATE), MONTH(sf.STUDY_FINANCE_MIDATE), 1)) AS report_end_date,
        DAY(EOMONTH(DATEFROMPARTS(YEAR(sf.STUDY_FINANCE_MIDATE), MONTH(sf.STUDY_FINANCE_MIDATE), 1))) AS report_period_days,
        COUNT(DISTINCT sd.STUDENT_ID) AS total_students,
        COUNT(DISTINCT CASE WHEN sss.CODE = 'NEW' THEN sd.STUDENT_ID END) AS new_enrollments,
        COUNT(DISTINCT CASE WHEN sss.CODE = 'ACTIVE' THEN sd.STUDENT_ID END) AS active_students,
        COUNT(*) AS total_financial_transactions,
        CAST(SUM(sf.NET_AMOUNT) AS DECIMAL(18,2)) AS total_amount_disbursed,
        CASE WHEN COUNT(*) = 0 THEN CAST(0 AS DECIMAL(18,2))
             ELSE CAST(SUM(sf.NET_AMOUNT) * 1.0 / COUNT(*) AS DECIMAL(18,2)) END AS avg_amount_per_transaction,
        CASE WHEN COUNT(DISTINCT sd.STUDENT_ID) = 0 THEN CAST(0 AS DECIMAL(18,2))
             ELSE CAST(SUM(sf.NET_AMOUNT) * 1.0 / COUNT(DISTINCT sd.STUDENT_ID) AS DECIMAL(18,2)) END AS avg_amount_per_student,
        CAST(SUM(CASE WHEN sty.IS_SCHOLARSHIP = 1 THEN sf.NET_AMOUNT ELSE 0 END) AS DECIMAL(18,2)) AS scholarship_amount,
        CAST(SUM(CASE WHEN sty.IS_SCHOLARSHIP = 0 THEN sf.NET_AMOUNT ELSE 0 END) AS DECIMAL(18,2)) AS regular_amount,
        COUNT(DISTINCT CASE WHEN sty.IS_SCHOLARSHIP = 1 THEN sd.STUDENT_ID END) AS scholarship_students,
        COUNT(DISTINCT CASE WHEN sty.IS_SCHOLARSHIP = 0 THEN sd.STUDENT_ID END) AS regular_students,
        COUNT(DISTINCT CASE WHEN sg.CODE = 'MALE' THEN sd.STUDENT_ID END) AS male_students,
        COUNT(DISTINCT CASE WHEN sg.CODE = 'FEMALE' THEN sd.STUDENT_ID END) AS female_students,
        COUNT(DISTINCT sd.STUDY_UNIVERSITY_IN) AS total_universities,
        COUNT(DISTINCT sd.STUDY_COUNTRY_IN) AS total_countries,
        COUNT(DISTINCT sd.STUDENT_MAJOR_ID) AS total_majors
    FROM MOHECORE.STUDENT_FINANCE sf
    INNER JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = sf.STUDENT_ID AND ISNULL(sd.IS_DELETED, 0) = 0
    LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
    LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE sty ON sty.STUDENT_STUDY_TYPE_ID = sd.STUDENT_STUDY_TYPE_ID
    LEFT JOIN MOHECORE.STUDENT_GENDER sg ON sg.STUDENT_GENDER_ID = sd.STUDENT_GENDER
    WHERE sf.STUDY_FINANCE_MIDATE IS NOT NULL
    GROUP BY sd.STUDY_COUNTRY_IN, YEAR(sf.STUDY_FINANCE_MIDATE), MONTH(sf.STUDY_FINANCE_MIDATE)
""")
@Synchronize({"STUDENT_FINANCE", "STUDENT_DATA", "STUDENT_STUDY_STATUS", "STUDENT_STUDY_TYPE", "STUDENT_GENDER"})
@IdClass(CustomPeriodReport.CustomPeriodReportId.class)
public class CustomPeriodReport {

    @Id
    @Column(name = "report_start_date")
    private LocalDate reportStartDate;

    @Id
    @Column(name = "study_country_id")
    private Integer countryId;

    @Column(name = "report_end_date")
    private LocalDate reportEndDate;

    @Column(name = "report_period_days")
    private Integer reportPeriodDays;

    @Column(name = "total_students")
    private Integer totalStudents;

    @Column(name = "new_enrollments")
    private Integer newEnrollments;

    @Column(name = "active_students")
    private Integer activeStudents;
    @Column(name = "total_financial_transactions")
    private Integer totalFinancialTransactions;

    @Column(name = "total_amount_disbursed")
    private BigDecimal totalAmountDisbursed;

    @Column(name = "avg_amount_per_transaction")
    private BigDecimal avgAmountPerTransaction;

    @Column(name = "avg_amount_per_student")
    private BigDecimal avgAmountPerStudent;

    @Column(name = "scholarship_amount")
    private BigDecimal scholarshipAmount;

    @Column(name = "regular_amount")
    private BigDecimal regularAmount;

    @Column(name = "scholarship_students")
    private Integer scholarshipStudents;

    @Column(name = "regular_students")
    private Integer regularStudents;

    @Column(name = "male_students")
    private Integer maleStudents;

    @Column(name = "female_students")
    private Integer femaleStudents;

    @Column(name = "total_universities")
    private Integer totalUniversities;

    @Column(name = "total_countries")
    private Integer totalCountries;

    @Column(name = "total_majors")
    private Integer totalMajors;

    @Getter
    @Setter
    @lombok.EqualsAndHashCode
    public static class CustomPeriodReportId implements Serializable {
        private LocalDate reportStartDate;
        private Integer countryId;
    }
}
