package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Immutable
@Table(name = "V_University_Partnership", schema = "MOHECORE")
public class UniversityPartnership {
    
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
    
    @Column(name = "total_programs")
    private Integer totalPrograms;
    
    @Column(name = "active_students")
    private Integer activeStudents;
    
    
    
    
    @Column(name = "degree_levels_offered")
    private Integer degreeLevelsOffered;
    
    @Column(name = "scholarship_students")
    private Integer scholarshipStudents;
    
    @Column(name = "regular_students")
    private Integer regularStudents;
}

