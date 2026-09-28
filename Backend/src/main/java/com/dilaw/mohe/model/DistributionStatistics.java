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
@Table(name = "V_Distribution_Statistics", schema = "MOHECORE")
@IdClass(DistributionStatistics.DistributionStatisticsId.class)
public class DistributionStatistics {
    
    @Id
    @Column(name = "distribution_type")
    private String distributionType;
    
    @Id
    @Column(name = "category_name")
    private String categoryName;

    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "category_name_en")
    private String categoryNameEn;
    
    @Column(name = "student_count")
    private Integer studentCount;
    
    @Column(name = "percentage_of_total")
    private BigDecimal percentageOfTotal;
    
    @Column(name = "rank_position")
    private Long rankPosition;
    
    @Column(name = "category_significance")
    private String categorySignificance;
    
    @Getter
    @Setter
    @lombok.EqualsAndHashCode
    public static class DistributionStatisticsId implements java.io.Serializable {
        private String distributionType;
        private String categoryName;
    }
}

