package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.PerformanceMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Performance metrics.
 *
 * <p>Three metrics are produced, all counted from live data: enrolled students,
 * total disbursed, and retention rate.
 *
 * <p><b>Targets are no longer invented.</b> The graduation metric used to carry
 * {@code CAST(70 AS DECIMAL(18,2))} as its target and derive on_target /
 * below_target by comparing against that same literal 70. No table, policy
 * document or ministry decision backed the number. Targets now come from
 * {@code REPORT_METRIC_TARGET} (V028), which is deliberately created empty: with
 * no row, {@code target_value} and {@code achievement_percentage} are NULL and
 * every metric reads "informational", which is the truth. Insert a row there to
 * activate a target — no code change needed.
 *
 * <p><b>Graduation became retention.</b> The graduation metric selected students
 * whose status name was {@code N'Graduated'}. This database has no such status,
 * so the metric was 0 by construction and permanently "below_target" against the
 * invented 70. Retention measures the same cohort health from the ACTIVE status,
 * which is recorded.
 *
 * <p>The legacy JPQL finders that read {@code V_Performance_Metrics} were
 * removed. The metrics come from the native query below, and the view had
 * drifted from it, so those finders returned categories and rows that no longer
 * corresponded to anything the page could show.
 */
@Repository
public interface PerformanceMetricsRepository extends JpaRepository<PerformanceMetrics, PerformanceMetrics.PerformanceMetricsId> {

    @Query(value = """
            SELECT metric_category, metric_name, metric_value, metric_unit, target_value,
                   achievement_percentage, performance_status, measurement_period
            FROM (
                SELECT
                    raw.metric_category,
                    raw.metric_name,
                    raw.metric_value,
                    raw.metric_unit,
                    CAST(tgt.TARGET_VALUE AS DECIMAL(18,2)) AS target_value,
                    CASE WHEN tgt.TARGET_VALUE IS NULL OR tgt.TARGET_VALUE = 0 THEN NULL
                         ELSE CAST(raw.metric_value * 100.0 / tgt.TARGET_VALUE AS DECIMAL(5,2)) END AS achievement_percentage,
                    CASE WHEN tgt.TARGET_VALUE IS NULL THEN 'informational'
                         WHEN raw.metric_value >= tgt.TARGET_VALUE THEN 'on_target'
                         ELSE 'below_target' END AS performance_status,
                    raw.measurement_period
                FROM (
                    SELECT
                        'enrollment' AS metric_category,
                        'total_students' AS metric_name,
                        CAST(COUNT(*) AS DECIMAL(18,2)) AS metric_value,
                        'count' AS metric_unit,
                        CAST(YEAR(GETDATE()) AS NVARCHAR(10)) AS measurement_period
                    FROM MOHECORE.STUDENT_DATA sd
                    WHERE ISNULL(sd.IS_DELETED, 0) = 0
                      AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
                    UNION ALL
                    SELECT
                        'finance', 'total_disbursed',
                        CAST(ISNULL(SUM(sf.NET_AMOUNT), 0) AS DECIMAL(18,2)),
                        -- Unit is deliberately neutral: STUDENT_FINANCE records no
                        -- currency (STUDY_CURRENCY_IN_ID is NULL on every row), so
                        -- naming one here would be an assertion, not a reading.
                        'amount',
                        CAST(YEAR(GETDATE()) AS NVARCHAR(10))
                    FROM MOHECORE.STUDENT_FINANCE sf
                    LEFT JOIN MOHECORE.STUDENT_DATA sd ON sf.STUDENT_ID = sd.STUDENT_ID
                    WHERE ISNULL(sd.IS_DELETED, 0) = 0
                      AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
                    UNION ALL
                    -- Retention replaces the former graduation-rate metric, which
                    -- counted students whose status name was 'Graduated'. No such
                    -- status exists in this database, so that metric was 0 by
                    -- construction. Retention measures the same cohort health from
                    -- data that is actually recorded.
                    SELECT
                        'retention', 'retention_rate',
                        CAST(COUNT(CASE WHEN sss.CODE = 'ACTIVE' THEN 1 END) * 100.0
                             / NULLIF(COUNT(*), 0) AS DECIMAL(5,2)),
                        '%',
                        CAST(YEAR(GETDATE()) AS NVARCHAR(10))
                    FROM MOHECORE.STUDENT_DATA sd
                    LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
                    WHERE ISNULL(sd.IS_DELETED, 0) = 0
                      AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
                ) raw
                LEFT JOIN MOHECORE.REPORT_METRIC_TARGET tgt
                       ON tgt.METRIC_CATEGORY = raw.metric_category
                      AND tgt.METRIC_NAME = raw.metric_name
                      AND (tgt.EFFECTIVE_YEAR IS NULL
                           OR CAST(tgt.EFFECTIVE_YEAR AS NVARCHAR(10)) = raw.measurement_period)
                      AND tgt.COUNTRY_ID IS NULL
            ) m
            WHERE (:category IS NULL OR metric_category = :category)
              AND (:status IS NULL OR performance_status = :status)
              AND (:period IS NULL OR measurement_period = :period)
              AND (:minPercentage IS NULL OR achievement_percentage >= :minPercentage)
            ORDER BY metric_category, metric_name
            """, nativeQuery = true)
    List<PerformanceMetrics> search(@Param("applyCountryFilter") boolean applyCountryFilter,
                                    @Param("countryIds") List<Integer> countryIds,
                                    @Param("category") String category,
                                    @Param("status") String status,
                                    @Param("period") String period,
                                    @Param("minPercentage") java.math.BigDecimal minPercentage);
}

