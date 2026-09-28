package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.AcademicYearReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Academic year report.
 *
 * <p>These native queries aggregate STUDENT_FINANCE ⨝ STUDENT_DATA directly; the
 * entity maps the same SELECT as a subselect (the legacy view is dropped by V066).
 *
 * <p><b>Status matching.</b> Earlier revisions selected students by the display
 * name of their study status, e.g. {@code IN (N'Graduated', N'متخرج')}. Display
 * names are user-editable and language-specific, and in this database none of
 * them said "Graduated" at all, so those predicates silently matched nothing.
 * Every predicate here now joins {@code STUDENT_STUDY_STATUS.CODE}, the stable
 * key added by V028.
 *
 * <p><b>Columns that report NULL, and why.</b> Eight columns used to be literal
 * {@code 0}, which reads as a measurement — "no student graduated" — when the
 * truth is that the figure is not recorded. Six are now genuinely computed
 * (new, transferred, avg duration, scholarship, regular, and the rates). The
 * graduation-related four report NULL instead:
 *
 * <ul>
 *   <li>{@code graduated_students}, {@code graduation_rate},
 *       {@code total_degrees_awarded}, {@code bachelors/masters/phd_awarded}</li>
 * </ul>
 *
 * This ministry does not record graduation anywhere. STUDENT_STUDY_STATUS has no
 * GRADUATED row, {@code V_Academic_Performance.is_graduated} is derived from the
 * same impossible name match and is 0 for all 941 students, and
 * STUDENT_YEAR_RESULT.Grade is NULL on all 5051 rows. Until a graduation record
 * exists, NULL is the only honest answer — it renders as an empty cell rather
 * than as a confident 0%.
 *
 * <p>{@code scholarship_students} + {@code regular_students} will not sum to
 * {@code total_enrolled_students}: STUDENT_STUDY_TYPE_ID is "Not Specified" on
 * 937 of 941 students, and unclassified students are counted in neither bucket
 * rather than being folded into "regular".
 */
@Repository
public interface AcademicYearReportRepository extends JpaRepository<AcademicYearReport, AcademicYearReport.AcademicYearReportId> {

    /*
     * The three queries below share one SELECT list; only the WHERE/HAVING and
     * ordering differ. Keep them in step when editing.
     */

    @Query(value = """
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
              AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
            GROUP BY sd.STUDY_COUNTRY_IN, YEAR(sf.STUDY_FINANCE_MIDATE)
            ORDER BY academic_year DESC
            """, nativeQuery = true)
    List<AcademicYearReport> findAllOrdered(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query(value = """
            SELECT TOP 1
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
              AND CAST(YEAR(sf.STUDY_FINANCE_MIDATE) AS NVARCHAR(10)) = :year
              AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
            GROUP BY sd.STUDY_COUNTRY_IN, YEAR(sf.STUDY_FINANCE_MIDATE)
            ORDER BY sd.STUDY_COUNTRY_IN
            """, nativeQuery = true)
    AcademicYearReport findByAcademicYear(
            @Param("year") String year,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    /**
     * Filters on retention rate, not graduation rate.
     *
     * <p>The method is still named for graduation because it backs the
     * {@code /by-minimum-graduation-rate} endpoint, but graduation is not
     * recorded in this database (see the class javadoc), so a graduation-rate
     * predicate could only ever return an empty list. Retention is the
     * equivalent figure this data can actually support.
     */
    @Query(value = """
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
              AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
            GROUP BY sd.STUDY_COUNTRY_IN, YEAR(sf.STUDY_FINANCE_MIDATE)
            HAVING CASE WHEN COUNT(DISTINCT sd.STUDENT_ID) = 0 THEN CAST(0 AS DECIMAL(5,2))
                     ELSE CAST(COUNT(DISTINCT CASE WHEN sss.CODE = 'ACTIVE' THEN sd.STUDENT_ID END) * 100.0
                          / COUNT(DISTINCT sd.STUDENT_ID) AS DECIMAL(5,2)) END >= :minRate
            ORDER BY retention_rate DESC
            """, nativeQuery = true)
    List<AcademicYearReport> findByMinimumGraduationRate(
            @Param("minRate") java.math.BigDecimal minRate,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);
}
