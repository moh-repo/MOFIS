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
@Table(name = "V_Export_Manager", schema = "MOHECORE")
public class ExportManager {
    
    @Id
    @Column(name = "export_id")
    private Long exportId;
    
    @Column(name = "report_name")
    private String reportName;
    
    /** The same name in English (V124); resolved by BilingualRows for an EN render. */
    @Column(name = "report_name_en")
    private String reportNameEn;
    
    @Column(name = "report_description")
    private String reportDescription;
    
    /** The same text in English (V124); resolved by BilingualRows for an EN render. */
    @Column(name = "report_description_en")
    private String reportDescriptionEn;
    
    @Column(name = "total_records")
    private Integer totalRecords;
    
    @Column(name = "export_status")
    private String exportStatus;
    
    @Column(name = "estimated_size_kb")
    private Integer estimatedSizeKb;
    
    @Column(name = "last_export_date")
    private LocalDateTime lastExportDate;
    
    @Column(name = "default_format")
    private String defaultFormat;
    
    @Column(name = "is_available")
    private Integer isAvailable;
    }

