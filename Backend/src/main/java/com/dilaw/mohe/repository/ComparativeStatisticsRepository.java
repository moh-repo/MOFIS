package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.ComparativeStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComparativeStatisticsRepository extends JpaRepository<ComparativeStatistics, ComparativeStatistics.ComparativeStatisticsId> {

    /**
     * The cohort years students can be compared across.
     *
     * <p>STUDENT_DELEGATION_DECISION_YEAR is nvarchar, so it is parsed with
     * TRY_CAST and anything unparseable is dropped rather than counted as year
     * zero. It is populated on 846 of the 941 students. The alternatives are not
     * usable: STUDENT_ADDED_QUARTER is set on 3 students and CURRENT_QUARTER
     * holds the same value ('20242') for every row.
     */
    @Query(value = """
            SELECT DISTINCT TRY_CAST(sd.STUDENT_DELEGATION_DECISION_YEAR AS INT) AS decision_year
            FROM MOHECORE.STUDENT_DATA sd
            WHERE ISNULL(sd.IS_DELETED, 0) = 0
              AND TRY_CAST(sd.STUDENT_DELEGATION_DECISION_YEAR AS INT) IS NOT NULL
              AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
            ORDER BY decision_year DESC
            """, nativeQuery = true)
    List<Integer> findDistinctDecisionYears(@Param("applyCountryFilter") boolean applyCountryFilter,
                                            @Param("countryIds") List<Integer> countryIds);

    /*
     * Counts students per dimension in two cohort years.
     *
     * The comparison used to be written as
     *
     *     SUM(CASE WHEN YEAR(sd.STUDENT_BIRTHDATE) = YEAR(GETDATE()) THEN 1 ELSE 0 END)
     *
     * which counted students by their year of birth against the calendar year,
     * so "2026" meant students born in 2026. No scholarship student is, and every
     * value on the page was zero with a null change. The periods themselves came
     * from GETDATE() rather than from any row, so the dropdowns offered two
     * numbers the data had no say in.
     *
     * Dimensions with no students in either year are dropped: a comparison row
     * of two zeros says nothing, and it used to be all the page displayed.
     */
    @Query(value = """
            SELECT comparison_type, dimension_name, dimension_name_en, current_period_value, previous_period_value,
                   CASE WHEN ISNULL(previous_period_value, 0) = 0 THEN NULL
                        ELSE CAST((current_period_value - previous_period_value) * 100.0
                                  / previous_period_value AS DECIMAL(8,2))
                   END AS change_percentage,
                   CAST(:currentPeriod AS NVARCHAR(10))  AS current_period,
                   CAST(:previousPeriod AS NVARCHAR(10)) AS previous_period
            FROM (
                SELECT 'country' AS comparison_type,
                       ISNULL(sci.STUDY_COUNTRY_IN_NAME, N'-') AS dimension_name,
                       ISNULL(COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME), N'-') AS dimension_name_en,
                       SUM(CASE WHEN TRY_CAST(sd.STUDENT_DELEGATION_DECISION_YEAR AS INT) = :currentPeriod  THEN 1 ELSE 0 END) AS current_period_value,
                       SUM(CASE WHEN TRY_CAST(sd.STUDENT_DELEGATION_DECISION_YEAR AS INT) = :previousPeriod THEN 1 ELSE 0 END) AS previous_period_value
                FROM MOHECORE.STUDENT_DATA sd
                LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
                WHERE ISNULL(sd.IS_DELETED, 0) = 0
                  AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
                GROUP BY sci.STUDY_COUNTRY_IN_NAME, sci.STUDY_COUNTRY_IN_NAME_EN
                UNION ALL
                SELECT 'degree',
                       ISNULL(sdeg.STUDENT_DEGREE_NAME, N'-'),
                       ISNULL(COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME), N'-'),
                       SUM(CASE WHEN TRY_CAST(sd.STUDENT_DELEGATION_DECISION_YEAR AS INT) = :currentPeriod  THEN 1 ELSE 0 END),
                       SUM(CASE WHEN TRY_CAST(sd.STUDENT_DELEGATION_DECISION_YEAR AS INT) = :previousPeriod THEN 1 ELSE 0 END)
                FROM MOHECORE.STUDENT_DATA sd
                LEFT JOIN MOHECORE.STUDENT_DEGREE sdeg ON sdeg.STUDENT_DEGREE_ID = sd.STUDENT_DEGREE_ID
                WHERE ISNULL(sd.IS_DELETED, 0) = 0
                  AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
                GROUP BY sdeg.STUDENT_DEGREE_NAME, sdeg.STUDENT_DEGREE_NAME_EN
            ) base
            WHERE (:comparisonType IS NULL OR comparison_type = :comparisonType)
              AND (current_period_value <> 0 OR previous_period_value <> 0)
            ORDER BY comparison_type, current_period_value DESC
            """, nativeQuery = true)
    List<ComparativeStatistics> findComparison(@Param("comparisonType") String comparisonType,
                                               @Param("currentPeriod") Integer currentPeriod,
                                               @Param("previousPeriod") Integer previousPeriod,
                                               @Param("applyCountryFilter") boolean applyCountryFilter,
                                               @Param("countryIds") List<Integer> countryIds);
}

