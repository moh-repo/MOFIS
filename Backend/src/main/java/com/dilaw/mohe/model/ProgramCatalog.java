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
@Table(name = "V_Program_Catalog", schema = "MOHECORE")
@IdClass(ProgramCatalog.ProgramCatalogId.class)
public class ProgramCatalog {
    
    @Id
    @Column(name = "university_id")
    private Long universityId;
    
    @Id
    @Column(name = "major_id")
    private Long majorId;
    
    @Id
    @Column(name = "degree_level_id")
    private Integer degreeLevelId;
    
    @Column(name = "university_name")
    private String universityName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "university_name_en")
    private String universityNameEn;
    
    @Column(name = "university_name_ar")
    private String universityNameAr;
    
    @Column(name = "major_name")
    private String majorName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "major_name_en")
    private String majorNameEn;
    
    @Column(name = "major_name_ar")
    private String majorNameAr;
    
    @Column(name = "degree_level_name")
    private String degreeLevelName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "degree_level_name_en")
    private String degreeLevelNameEn;
    
    @Column(name = "country_name")
    private String countryName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "country_name_en")
    private String countryNameEn;

    @Column(name = "country_id")
    private Integer countryId;
    
    @Column(name = "total_students_enrolled")
    private Integer totalStudentsEnrolled;
    
    @Column(name = "current_students")
    private Integer currentStudents;
    
    
    
    
    
    @Getter
    @Setter
    @lombok.EqualsAndHashCode
    public static class ProgramCatalogId implements java.io.Serializable {
        private Long universityId;
        private Long majorId;
        private Integer degreeLevelId;
    }
}

