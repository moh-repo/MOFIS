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
@Table(name = "V_Performance_Metrics", schema = "MOHECORE")
@IdClass(PerformanceMetrics.PerformanceMetricsId.class)
public class PerformanceMetrics {
    
    @Id
    @Column(name = "metric_category")
    private String metricCategory;
    
    @Id
    @Column(name = "metric_name")
    private String metricName;
    
    @Column(name = "metric_value")
    private BigDecimal metricValue;
    
    @Column(name = "metric_unit")
    private String metricUnit;
    
    @Column(name = "target_value")
    private BigDecimal targetValue;
    
    @Column(name = "achievement_percentage")
    private BigDecimal achievementPercentage;
    
    @Column(name = "performance_status")
    private String performanceStatus;
    
    @Column(name = "measurement_period")
    private String measurementPeriod;
    
    @Getter
    @Setter
    @lombok.EqualsAndHashCode
    public static class PerformanceMetricsId implements java.io.Serializable {
        private String metricCategory;
        private String metricName;
    }
}

