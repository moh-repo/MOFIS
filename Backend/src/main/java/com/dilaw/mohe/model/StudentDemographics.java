package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Getter
@Setter
@Entity
@Immutable
@Table(name = "V_Student_Demographics", schema = "MOHECORE")
public class StudentDemographics {
    
    @Id
    @Column(name = "STUDENT_ID")
    private Integer studentId;
    
    @Column(name = "STUDENT_NAME")
    private String studentName;
    
    /**
     * Tenant key: the country the student studies in.
     *
     * <p>This is what UMSCORE.UserCountries holds and what every country-scope
     * predicate must filter on. Do not confuse it with {@link #studentCountryId},
     * which is the student's nationality and comes from a different lookup table
     * with overlapping ids — id 1 is Egypt here and Yemen there.
     */
    @Column(name = "study_country_in_id")
    private Integer studyCountryInId;

    /** The student's home/nationality country. Report content, never the tenant key. */
    @Column(name = "STUDENT_COUNTRY_ID")
    private Integer studentCountryId;
    
    @Column(name = "STUDENT_COUNTRY_NAME")
    private String studentCountryName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_COUNTRY_NAME_EN")
    private String studentCountryNameEn;
    
    @Column(name = "STUDENT_GENDER")
    private String studentGenderName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_GENDER_EN")
    private String studentGenderNameEn;
    
    @Column(name = "student_age")
    private Integer studentAge;
    
    @Column(name = "STUDENT_CURRENT_LEVEL_ID")
    private Integer studentCurrentLevelId;
    
    @Column(name = "STUDENT_CURRENT_LEVEL_NAME")
    private String studentCurrentLevelName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_CURRENT_LEVEL_NAME_EN")
    private String studentCurrentLevelNameEn;
    
    @Column(name = "STUDENT_STUDY_TYPE_ID")
    private Integer studentStudyTypeId;
    
    @Column(name = "STUDENT_STUDY_TYPE_NAME")
    private String studentStudyTypeName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_STUDY_TYPE_NAME_EN")
    private String studentStudyTypeNameEn;
    
    @Column(name = "STUDENT_DEGREE_ID")
    private Integer studentDegreeId;
    
    @Column(name = "STUDENT_DEGREE_NAME")
    private String studentDegreeName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_DEGREE_NAME_EN")
    private String studentDegreeNameEn;
    
    @Column(name = "STUDENT_MAJOR_ID")
    private Integer studentMajorId;
    
    @Column(name = "STUDENT_MAJOR_NAME")
    private String studentMajorName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_MAJOR_NAME_EN")
    private String studentMajorNameEn;
    
    @Column(name = "STUDENT_STUDY_STATUS_ID")
    private Integer studentStudyStatusId;
    
    @Column(name = "STUDENT_STUDY_STATUS_NAME")
    private String studentStudyStatusName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_STUDY_STATUS_NAME_EN")
    private String studentStudyStatusNameEn;
}

