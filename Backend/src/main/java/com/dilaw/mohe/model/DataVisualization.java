package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Getter
@Setter
@Entity
@Immutable
@Table(name = "V_Data_Visualization", schema = "MOHECORE")
public class DataVisualization {
    
    @Id
    @Column(name = "visualization_id")
    private Long visualizationId;
    
    @Column(name = "chart_name")
    private String chartName;

    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "chart_name_en")
    private String chartNameEn;
    
    @Column(name = "chart_type")
    private String chartType;
    
    @Column(name = "category")
    private String category;
    
    @Column(name = "data_value")
    private Integer dataValue;
    
    @Column(name = "data_label")
    private String dataLabel;

    /** The same label in English; resolved by BilingualRows for an EN render. */
    @Column(name = "data_label_en")
    private String dataLabelEn;
    
    @Column(name = "color_code")
    private String colorCode;
    
    @Column(name = "sort_order")
    private Integer sortOrder;
}

