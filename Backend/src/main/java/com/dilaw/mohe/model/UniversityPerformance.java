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
@Table(name = "V_University_Performance", schema = "MOHECORE")
public class UniversityPerformance {
    
    @Id
    @Column(name = "university_id")
    private Long universityId;
    
    @Column(name = "university_name")
    private String universityName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "university_name_en")
    private String universityNameEn;
    
    @Column(name = "university_name_ar")
    private String universityNameAr;
    
    @Column(name = "country_name")
    private String countryName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "country_name_en")
    private String countryNameEn;

    @Column(name = "country_id")
    private Integer countryId;
    
    @Column(name = "total_enrolled")
    private Integer totalEnrolled;
    
    @Column(name = "currently_active")
    private Integer currentlyActive;
    
    @Column(name = "total_suspended")
    private Integer totalSuspended;
    
    @Column(name = "retention_rate")
    private BigDecimal retentionRate;
    
    @Column(name = "avg_study_duration")
    private BigDecimal avgStudyDuration;
    
    @Column(name = "programs_offered")
    private Integer programsOffered;
    
    @Column(name = "degree_levels")
    private Integer degreeLevels;
    
    @Column(name = "first_enrollment_year")
    private Integer firstEnrollmentYear;
    
    @Column(name = "last_enrollment_year")
    private Integer lastEnrollmentYear;
}

