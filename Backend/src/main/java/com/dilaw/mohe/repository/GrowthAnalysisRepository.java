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

    @Query(value = """
            WITH yearly AS (
                SELECT
                    YEAR(GETDATE()) - n AS analysis_year,
                    'total_students' AS growth_category,
                    COUNT(sd.STUDENT_ID) AS cnt
                FROM MOHECORE.STUDENT_DATA sd
                CROSS JOIN (VALUES (0),(1),(2),(3),(4)) AS y(n)
                WHERE ISNULL(sd.IS_DELETED, 0) = 0
                  AND TRY_CAST(sd.STUDENT_DELEGATION_DECISION_YEAR AS INT) <= YEAR(GETDATE()) - n
                  AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
                GROUP BY n
            ),
            computed AS (
                SELECT
                    growth_category,
                    analysis_year,
                    CAST(cnt AS DECIMAL(18,2)) AS current_value,
                    CAST(LAG(cnt) OVER (ORDER BY analysis_year) AS DECIMAL(18,2)) AS previous_value,
                    CASE
                        WHEN LAG(cnt) OVER (ORDER BY analysis_year) = 0 THEN NULL
                        ELSE CAST((cnt - LAG(cnt) OVER (ORDER BY analysis_year)) * 100.0
                             / LAG(cnt) OVER (ORDER BY analysis_year) AS DECIMAL(8,2))
                    END AS growth_rate,
                    CAST(cnt - LAG(cnt) OVER (ORDER BY analysis_year) AS DECIMAL(18,2)) AS absolute_growth,
                    CASE
                        WHEN cnt > LAG(cnt) OVER (ORDER BY analysis_year) THEN 'growing'
                        WHEN cnt < LAG(cnt) OVER (ORDER BY analysis_year) THEN 'declining'
                        ELSE 'stable'
                    END AS growth_status
                FROM yearly
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

