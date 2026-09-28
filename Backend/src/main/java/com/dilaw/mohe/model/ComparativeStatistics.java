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
@Table(name = "V_Comparative_Statistics", schema = "MOHECORE")
@IdClass(ComparativeStatistics.ComparativeStatisticsId.class)
public class ComparativeStatistics {
    
    @Id
    @Column(name = "comparison_type")
    private String comparisonType;
    
    @Id
    @Column(name = "dimension_name")
    private String dimensionName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "dimension_name_en")
    private String dimensionNameEn;
    
    @Column(name = "current_period_value")
    private Integer currentPeriodValue;
    
    @Column(name = "previous_period_value")
    private Integer previousPeriodValue;
    
    @Column(name = "change_percentage")
    private BigDecimal changePercentage;
    
    @Column(name = "current_period")
    private String currentPeriod;
    
    @Column(name = "previous_period")
    private String previousPeriod;
    
    @Getter
    @Setter
    @lombok.EqualsAndHashCode
    public static class ComparativeStatisticsId implements java.io.Serializable {
        private String comparisonType;
        private String dimensionName;
    }
}

