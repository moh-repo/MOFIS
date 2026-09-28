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
@Table(name = "V_Growth_Analysis", schema = "MOHECORE")
@IdClass(GrowthAnalysis.GrowthAnalysisId.class)
public class GrowthAnalysis {
    
    @Id
    @Column(name = "growth_category")
    private String growthCategory;
    
    @Id
    @Column(name = "analysis_year")
    private Integer analysisYear;
    
    @Column(name = "current_value")
    private BigDecimal currentValue;
    
    @Column(name = "previous_value")
    private BigDecimal previousValue;
    
    @Column(name = "growth_rate")
    private BigDecimal growthRate;
    
    @Column(name = "absolute_growth")
    private BigDecimal absoluteGrowth;
    
    @Column(name = "growth_status")
    private String growthStatus;
    
    @Getter
    @Setter
    @lombok.EqualsAndHashCode
    public static class GrowthAnalysisId implements java.io.Serializable {
        private String growthCategory;
        private Integer analysisYear;
    }
}

