package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.GrowthAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GrowthAnalysisRepository extends JpaRepository<GrowthAnalysis, GrowthAnalysis.GrowthAnalysisId> {
    
    @Query("SELECT g FROM GrowthAnalysis g WHERE g.growthCategory = :category ORDER BY g.analysisYear")
    List<GrowthAnalysis> findByCategory(@Param("category") String category);
    
    @Query("SELECT g FROM GrowthAnalysis g WHERE g.growthStatus = :status ORDER BY g.growthRate DESC")
    List<GrowthAnalysis> findByGrowthStatus(@Param("status") String status);
    
    @Query("SELECT g FROM GrowthAnalysis g WHERE g.analysisYear >= :startYear AND g.analysisYear <= :endYear ORDER BY g.analysisYear, g.growthCategory")
    List<GrowthAnalysis> findByYearRange(@Param("startYear") Integer startYear, @Param("endYear") Integer endYear);
    
    @Query("SELECT g FROM GrowthAnalysis g WHERE g.growthRate > :minRate ORDER BY g.growthRate DESC")
    List<GrowthAnalysis> findByMinimumGrowthRate(@Param("minRate") java.math.BigDecimal minRate);
    
    @Query("SELECT DISTINCT g.growthCategory FROM GrowthAnalysis g ORDER BY g.growthCategory")
    List<String> findAllGrowthCategories();

    /**
     * Year-over-year growth of the students delegated each year, for the current year
     * and the four before it.
     *
     * <p>RPT-07. This counted every student delegated <em>up to</em> each year — a
     * running total, which can only rise, so {@code declining} could never appear and
     * the rate measured how the total accumulated rather than how intake changed. A year
     * with no delegations dropped out of the result altogether (the cross join produced
     * no row for it), so the next year was compared with the one before the gap. Now:
     * <ul>
     *   <li>each year counts the students delegated in that year;</li>
     *   <li>all five years are generated and a year with none counts 0;</li>
     *   <li>each year is compared with the year immediately before it
     *       ({@code LAG} over a gap-free series);</li>
     *   <li>the first year has nothing to be compared with and says so:
     *       {@link #BASELINE_STATUS}, with no previous value, rate or change.</li>
     * </ul>
     * A rise from 0 is growing with no rate (a percentage of zero is undefined).
     */
    String BASELINE_STATUS = "baseline";

    @Query(value = """
            WITH years AS (
                SELECT YEAR(GETDATE()) - n AS analysis_year
                FROM (VALUES (0),(1),(2),(3),(4)) AS y(n)
            ),
            yearly AS (
                SELECT
                    yr.analysis_year,
                    'total_students' AS growth_category,
                    COUNT(sd.STUDENT_ID) AS cnt
                FROM years yr
                LEFT JOIN MOHECORE.STUDENT_DATA sd
                       ON TRY_CAST(sd.STUDENT_DELEGATION_DECISION_YEAR AS INT) = yr.analysis_year
                      AND ISNULL(sd.IS_DELETED, 0) = 0
                      AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
                GROUP BY yr.analysis_year
            ),
            lagged AS (
                SELECT
                    growth_category,
                    analysis_year,
                    cnt,
                    LAG(cnt) OVER (PARTITION BY growth_category ORDER BY analysis_year) AS prev_cnt
                FROM yearly
            ),
            computed AS (
                SELECT
                    growth_category,
                    analysis_year,
                    CAST(cnt AS DECIMAL(18,2)) AS current_value,
                    CAST(prev_cnt AS DECIMAL(18,2)) AS previous_value,
                    CASE
                        WHEN prev_cnt IS NULL OR prev_cnt = 0 THEN NULL
                        ELSE CAST((cnt - prev_cnt) * 100.0 / prev_cnt AS DECIMAL(8,2))
                    END AS growth_rate,
                    CAST(cnt - prev_cnt AS DECIMAL(18,2)) AS absolute_growth,
                    CASE
                        WHEN prev_cnt IS NULL THEN 'baseline'
                        WHEN cnt > prev_cnt THEN 'growing'
                        WHEN cnt < prev_cnt THEN 'declining'
                        ELSE 'stable'
                    END AS growth_status
                FROM lagged
            )
            SELECT growth_category, analysis_year, current_value, previous_value,
                   growth_rate, absolute_growth, growth_status
            FROM computed
            WHERE (:category IS NULL OR growth_category = :category)
              AND (:status IS NULL OR growth_status = :status)
              AND (:startYear IS NULL OR analysis_year >= :startYear)
              AND (:endYear IS NULL OR analysis_year <= :endYear)
              AND (:minRate IS NULL OR growth_rate >= :minRate)
            ORDER BY analysis_year, growth_category
            """, nativeQuery = true)
    List<GrowthAnalysis> search(@Param("applyCountryFilter") boolean applyCountryFilter,
                                @Param("countryIds") List<Integer> countryIds,
                                @Param("category") String category,
                                @Param("status") String status,
                                @Param("startYear") Integer startYear,
                                @Param("endYear") Integer endYear,
                                @Param("minRate") java.math.BigDecimal minRate);
}

