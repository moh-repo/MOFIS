package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.math.BigDecimal;

/**
 * Three-year linear extrapolation of yearly disbursement.
 *
 * <p>Mapped as a subselect rather than a view. The entity used to map
 * {@code V_Predictive_Analytics}, whose columns had drifted from it
 * ({@code historical_avg} against the entity's {@code historical_average}), so a
 * plain {@code findAll()} failed with "Invalid column name" and only the
 * repository's native query — whose aliases match — ever worked. The subselect
 * is that query without its filter parameters; V066 drops the view. The
 * repository's {@code search} still runs the parameterised form.
 */
@Getter
@Setter
@Entity
@Immutable
@Subselect("""
    SELECT
        'finance_amount' AS forecast_category,
        YEAR(GETDATE()) + y.n AS forecast_year,
        ad.historical_average,
        CASE WHEN ad.historical_average = 0 THEN NULL
             ELSE CAST(ad.slope / ad.historical_average * 100 AS DECIMAL(8,2))
        END AS growth_rate,
        ad.historical_average + ad.slope * y.n AS predicted_value,
        CASE WHEN ABS(ISNULL(ad.slope / NULLIF(ad.historical_average, 0), 0)) < 0.05 THEN 'HIGH'
             WHEN ABS(ISNULL(ad.slope / NULLIF(ad.historical_average, 0), 0)) < 0.15 THEN 'MEDIUM'
             ELSE 'LOW'
        END AS confidence_level,
        CASE WHEN ISNULL(ad.slope, 0) > 0 THEN 'upward'
             WHEN ISNULL(ad.slope, 0) < 0 THEN 'downward'
             ELSE 'stable'
        END AS forecast_trend
    FROM (
        SELECT
            AVG(CAST(hist.yr_total AS DECIMAL(18,2))) AS historical_average,
            AVG(CAST(hist.yr_count AS DECIMAL(18,2))) AS avg_count,
            CASE
                WHEN COUNT(*) < 2 THEN NULL
                ELSE (MAX(CAST(hist.yr_total AS DECIMAL(18,2))) - MIN(CAST(hist.yr_total AS DECIMAL(18,2)))) /
                     NULLIF(MAX(hist.yr) - MIN(hist.yr), 0)
            END AS slope
        FROM (
            SELECT
                YEAR(sf.STUDY_FINANCE_MIDATE) AS yr,
                SUM(CAST(sf.NET_AMOUNT AS BIGINT)) AS yr_total,
                COUNT(*) AS yr_count
            FROM MOHECORE.STUDENT_FINANCE sf
            WHERE sf.STUDY_FINANCE_MIDATE IS NOT NULL
              AND YEAR(sf.STUDY_FINANCE_MIDATE) >= YEAR(GETDATE()) - 4
              AND EXISTS (SELECT 1 FROM MOHECORE.STUDENT_DATA live_sd
                          WHERE live_sd.STUDENT_ID = sf.STUDENT_ID AND ISNULL(live_sd.IS_DELETED, 0) = 0)
            GROUP BY YEAR(sf.STUDY_FINANCE_MIDATE)
        ) hist
    ) ad
    CROSS JOIN (VALUES (1),(2),(3)) AS y(n)
""")
@Synchronize({"STUDENT_FINANCE"})
@IdClass(PredictiveAnalytics.PredictiveAnalyticsId.class)
public class PredictiveAnalytics {

    @Id
    @Column(name = "forecast_category")
    private String forecastCategory;

    @Id
    @Column(name = "forecast_year")
    private Integer forecastYear;

    @Column(name = "historical_average")
    private BigDecimal historicalAverage;

    @Column(name = "growth_rate")
    private BigDecimal growthRate;

    @Column(name = "predicted_value")
    private BigDecimal predictedValue;

    @Column(name = "confidence_level")
    private String confidenceLevel;

    @Column(name = "forecast_trend")
    private String forecastTrend;

    @Getter
    @Setter
    @lombok.EqualsAndHashCode
    public static class PredictiveAnalyticsId implements java.io.Serializable {
        private String forecastCategory;
        private Integer forecastYear;
    }
}
