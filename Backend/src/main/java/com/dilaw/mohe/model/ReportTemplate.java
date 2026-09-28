package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Immutable
@Table(name = "V_Report_Template", schema = "MOHECORE")
public class ReportTemplate {
    
    @Id
    @Column(name = "template_id")
    private Long templateId;
    
    @Column(name = "template_name")
    private String templateName;
    
    @Column(name = "template_description")
    private String templateDescription;
    
    @Column(name = "report_type")
    private String reportType;
    
    @Column(name = "filter_configuration")
    private String filterConfiguration;
    
    @Column(name = "selected_columns")
    private String selectedColumns;
    
    @Column(name = "estimated_records")
    private Integer estimatedRecords;
    
    @Column(name = "created_date")
    private LocalDateTime createdDate;
    
    @Column(name = "last_used_date")
    private LocalDateTime lastUsedDate;
    
    @Column(name = "usage_count")
    private Integer usageCount;
    
    @Column(name = "is_public")
    private Integer isPublic;
    }

