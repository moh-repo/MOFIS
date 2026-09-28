package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Immutable
@Table(name = "V_Trend_Analysis", schema = "MOHECORE")
@IdClass(TrendAnalysis.TrendAnalysisId.class)
public class TrendAnalysis {
    
    @Id
    @Column(name = "trend_period")
    private String trendPeriod;
    
    @Id
    @Column(name = "metric_type")
    private String metricType;
    
    @Column(name = "trend_year")
    private Integer trendYear;
    
    @Column(name = "trend_month")
    private Integer trendMonth;
    
    @Column(name = "metric_value")
    private BigDecimal metricValue;
    
    @Column(name = "previous_value")
    private BigDecimal previousValue;
    
    @Column(name = "growth_percentage")
    private BigDecimal growthPercentage;
    
    @Column(name = "trend_direction")
    private String trendDirection;
    
    @Getter
    @Setter
    @lombok.EqualsAndHashCode
    public static class TrendAnalysisId implements java.io.Serializable {
        private String trendPeriod;
        private String metricType;
    }
}

