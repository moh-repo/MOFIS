package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Immutable
@Table(name = "V_University_Agreement", schema = "MOHECORE")
public class UniversityAgreement {
    
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
    
    
    
    @Column(name = "total_students_under_agreement")
    private Integer totalStudentsUnderAgreement;
    
    @Column(name = "active_students")
    private Integer activeStudents;
    
    @Column(name = "programs_covered")
    private Integer programsCovered;
    
    @Column(name = "degree_levels_covered")
    private Integer degreeLevelsCovered;
    
    
    
}

