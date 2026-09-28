package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.TrendAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Trend rows are built by the query below, not read from {@code V_Trend_Analysis}.
 *
 * <p>Seven JPQL finders used to sit here reading the view directly. None was
 * called, and all of them would have returned a different answer from the query
 * below — the view periods by the record's last-modified timestamp and sums the
 * gross amount, and it has no country column, so it cannot honour report scope.
 * They were removed rather than left as a second, wrong source.
 */
@Repository
public interface TrendAnalysisRepository extends JpaRepository<TrendAnalysis, TrendAnalysis.TrendAnalysisId> {

    /*
     * The period is the month the money belongs to: the cheque date where one is
     * recorded, otherwise STUDENT_FIN_RECIEVED_YEAR / _MONTH.
     *
     * It used to be STUDY_FINANCE_MIDATE, the row's last-modified timestamp, so
     * the chart plotted when records were written rather than when payments were
     * made. With 939 of 941 rows loaded in one go, that produced a two-point
     * "trend" — Nov 2025 and May 2026 — collapsing 99.79%, for months in which no
     * scholarship payment was actually made. The real spending sits in Feb 2024.
     */
    @Query(value = """
            WITH base AS (
                SELECT
                    COALESCE(YEAR(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_YEAR) AS yr,
                    COALESCE(MONTH(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_MONTH) AS mo,
                    SUM(CAST(COALESCE(sf.NET_AMOUNT, 0) AS BIGINT)) AS total_amount
                FROM MOHECORE.STUDENT_FINANCE sf
                LEFT JOIN MOHECORE.STUDENT_DATA sd ON sf.STUDENT_ID = sd.STUDENT_ID
                WHERE COALESCE(YEAR(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_YEAR) IS NOT NULL
                  AND COALESCE(MONTH(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_MONTH) BETWEEN 1 AND 12
                  AND ISNULL(sd.IS_DELETED, 0) = 0
                  AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
                GROUP BY COALESCE(YEAR(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_YEAR),
                         COALESCE(MONTH(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_MONTH)
            ),
            ranked AS (
                SELECT
                    CAST(yr AS NVARCHAR(4)) + '-' + RIGHT('0' + CAST(mo AS NVARCHAR(2)), 2) AS trend_period,
                    'finance_amount' AS metric_type,
                    yr AS trend_year,
                    mo AS trend_month,
                    CAST(total_amount AS DECIMAL(18,2)) AS metric_value,
                    LAG(CAST(total_amount AS DECIMAL(18,2))) OVER (ORDER BY yr, mo) AS previous_value,
                    CASE
                        WHEN LAG(total_amount) OVER (ORDER BY yr, mo) = 0 THEN NULL
                        ELSE CAST((total_amount - LAG(total_amount) OVER (ORDER BY yr, mo)) * 100.0
                             / LAG(total_amount) OVER (ORDER BY yr, mo) AS DECIMAL(8,2))
                    END AS growth_percentage,
                    CASE
                        WHEN total_amount > LAG(total_amount) OVER (ORDER BY yr, mo) THEN 'up'
                        WHEN total_amount < LAG(total_amount) OVER (ORDER BY yr, mo) THEN 'down'
                        ELSE 'flat'
                    END AS trend_direction
                FROM base
            )
            SELECT trend_period, metric_type, trend_year, trend_month, metric_value,
                   previous_value, growth_percentage, trend_direction
            FROM ranked
            WHERE (:metricType IS NULL OR metric_type = :metricType)
              AND (:direction IS NULL OR trend_direction = :direction)
              AND (:startYear IS NULL OR trend_year >= :startYear)
              AND (:endYear IS NULL OR trend_year <= :endYear)
            ORDER BY trend_year, trend_month, metric_type
            """, nativeQuery = true)
    List<TrendAnalysis> findByFilters(@Param("metricType") String metricType,
                                      @Param("direction") String direction,
                                      @Param("startYear") Integer startYear,
                                      @Param("endYear") Integer endYear,
                                      @Param("applyCountryFilter") boolean applyCountryFilter,
                                      @Param("countryIds") List<Integer> countryIds);
}

