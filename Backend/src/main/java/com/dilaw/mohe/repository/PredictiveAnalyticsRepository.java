package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.PredictiveAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PredictiveAnalyticsRepository extends JpaRepository<PredictiveAnalytics, PredictiveAnalytics.PredictiveAnalyticsId> {
    
    @Query("SELECT p FROM PredictiveAnalytics p WHERE p.forecastCategory = :category ORDER BY p.forecastYear")
    List<PredictiveAnalytics> findByForecastCategory(@Param("category") String category);
    
    @Query("SELECT p FROM PredictiveAnalytics p WHERE p.forecastYear = :year ORDER BY p.forecastCategory")
    List<PredictiveAnalytics> findByForecastYear(@Param("year") Integer year);
    
    @Query("SELECT p FROM PredictiveAnalytics p WHERE p.confidenceLevel = :level ORDER BY p.predictedValue DESC")
    List<PredictiveAnalytics> findByConfidenceLevel(@Param("level") String level);
    
    @Query("SELECT p FROM PredictiveAnalytics p WHERE p.forecastTrend LIKE %:trend% ORDER BY p.predictedValue DESC")
    List<PredictiveAnalytics> findByForecastTrend(@Param("trend") String trend);
    
    @Query("SELECT DISTINCT p.forecastCategory FROM PredictiveAnalytics p ORDER BY p.forecastCategory")
    List<String> findAllForecastCategories();

    @Query(value = """
            WITH hist AS (
                SELECT
                    YEAR(sf.STUDY_FINANCE_MIDATE) AS yr,
                    SUM(CAST(sf.NET_AMOUNT AS BIGINT)) AS yr_total,
                    COUNT(*) AS yr_count
                FROM MOHECORE.STUDENT_FINANCE sf
                LEFT JOIN MOHECORE.STUDENT_DATA sd ON sf.STUDENT_ID = sd.STUDENT_ID
                WHERE sf.STUDY_FINANCE_MIDATE IS NOT NULL
                  AND ISNULL(sd.IS_DELETED, 0) = 0
                  AND YEAR(sf.STUDY_FINANCE_MIDATE) >= YEAR(GETDATE()) - 4
                  AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
                GROUP BY YEAR(sf.STUDY_FINANCE_MIDATE)
            ),
            avg_data AS (
                SELECT
                    AVG(CAST(yr_total AS DECIMAL(18,2))) AS historical_average,
                    AVG(CAST(yr_count AS DECIMAL(18,2))) AS avg_count,
                    CASE
                        WHEN COUNT(*) < 2 THEN NULL
                        ELSE (MAX(CAST(yr_total AS DECIMAL(18,2))) - MIN(CAST(yr_total AS DECIMAL(18,2)))) /
                             NULLIF(MAX(yr) - MIN(yr), 0)
                    END AS slope
                FROM hist
            )
            SELECT
                'finance_amount' AS forecast_category,
                YEAR(GETDATE()) + n AS forecast_year,
                ad.historical_average,
                CASE WHEN ad.historical_average = 0 THEN NULL
                     ELSE CAST(ad.slope / ad.historical_average * 100 AS DECIMAL(8,2))
                END AS growth_rate,
                ad.historical_average + ad.slope * n AS predicted_value,
                CASE WHEN ABS(ISNULL(ad.slope / NULLIF(ad.historical_average, 0), 0)) < 0.05 THEN 'HIGH'
                     WHEN ABS(ISNULL(ad.slope / NULLIF(ad.historical_average, 0), 0)) < 0.15 THEN 'MEDIUM'
                     ELSE 'LOW'
                END AS confidence_level,
                CASE WHEN ISNULL(ad.slope, 0) > 0 THEN 'upward'
                     WHEN ISNULL(ad.slope, 0) < 0 THEN 'downward'
                     ELSE 'stable'
                END AS forecast_trend
            FROM avg_data ad
            CROSS JOIN (VALUES (1),(2),(3)) AS y(n)
            WHERE (:category IS NULL OR 'finance_amount' = :category)
              AND (:year IS NULL OR YEAR(GETDATE()) + n = :year)
              AND (:level IS NULL OR (
                    CASE WHEN ABS(ISNULL(ad.slope / NULLIF(ad.historical_average, 0), 0)) < 0.05 THEN 'HIGH'
                         WHEN ABS(ISNULL(ad.slope / NULLIF(ad.historical_average, 0), 0)) < 0.15 THEN 'MEDIUM'
                         ELSE 'LOW' END) = :level)
              AND (:trend IS NULL OR (
                    CASE WHEN ISNULL(ad.slope, 0) > 0 THEN 'upward'
                         WHEN ISNULL(ad.slope, 0) < 0 THEN 'downward'
                         ELSE 'stable' END) LIKE CONCAT('%', :trend, '%'))
            ORDER BY forecast_year, forecast_category
            """, nativeQuery = true)
    List<PredictiveAnalytics> search(@Param("applyCountryFilter") boolean applyCountryFilter,
                                     @Param("countryIds") List<Integer> countryIds,
                                     @Param("category") String category,
                                     @Param("year") Integer year,
                                     @Param("level") String level,
                                     @Param("trend") String trend);
}

