package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.CustomPeriodReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Custom period report.
 *
 * <p>Four columns were literals and are now computed: {@code new_enrollments}
 * from the NEW study status, and {@code regular_amount} /
 * {@code scholarship_students} / {@code regular_students} from
 * {@code STUDENT_STUDY_TYPE.IS_SCHOLARSHIP} (V028). {@code scholarship_amount}
 * previously received the entire {@code SUM(NET_AMOUNT)} and now receives only
 * the funded share.
 *
 * <p>Status predicates key on {@code STUDENT_STUDY_STATUS.CODE} rather than on
 * display names, which are user-editable and language-specific.
 * {@code graduated_students} reports NULL because this ministry records no
 * graduation at all — see {@link AcademicYearReportRepository} for the evidence.
 */
@Repository
public interface CustomPeriodReportRepository extends JpaRepository<CustomPeriodReport, CustomPeriodReport.CustomPeriodReportId> {

    @Query(value = """
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
              AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
            GROUP BY sd.STUDY_COUNTRY_IN, YEAR(sf.STUDY_FINANCE_MIDATE), MONTH(sf.STUDY_FINANCE_MIDATE)
            ORDER BY report_start_date DESC
            """, nativeQuery = true)
    List<CustomPeriodReport> findAllFiltered(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);
}
