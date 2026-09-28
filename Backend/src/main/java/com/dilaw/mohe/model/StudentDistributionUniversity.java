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
@Table(name = "V_Student_Distribution_University", schema = "MOHECORE")
public class StudentDistributionUniversity {
    
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
    
    @Column(name = "country_name_ar")
    private String countryNameAr;
    
    @Column(name = "total_students")
    private Integer totalStudents;
    
    @Column(name = "active_students")
    private Integer activeStudents;
    
    @Column(name = "suspended_students")
    private Integer suspendedStudents;
    
    @Column(name = "male_students")
    private Integer maleStudents;
    
    @Column(name = "female_students")
    private Integer femaleStudents;
    
    @Column(name = "scholarship_students")
    private Integer scholarshipStudents;
    
    @Column(name = "regular_students")
    private Integer regularStudents;
    
    @Column(name = "bachelors_students")
    private Integer bachelorsStudents;
    
    @Column(name = "masters_students")
    private Integer mastersStudents;
    
    @Column(name = "phd_students")
    private Integer phdStudents;
    
    @Column(name = "programs_count")
    private Integer programsCount;
    
    @Column(name = "percentage_of_total_students")
    private BigDecimal percentageOfTotalStudents;
}

