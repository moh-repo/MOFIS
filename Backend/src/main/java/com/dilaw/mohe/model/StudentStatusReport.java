package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;

@Getter
@Setter
@Entity
@Immutable
@Table(name = "V_Student_Status_Report", schema = "MOHECORE")
@IdClass(StudentStatusReport.StudentStatusReportId.class)
public class StudentStatusReport {
    
    @Id
    @Column(name = "STUDENT_STUDY_STATUS_ID")
    private Integer studentStudyStatusId;
    
    /**
     * Tenant key: the country the student studies in.
     *
     * <p>This is what UMSCORE.UserCountries holds and what every country-scope
     * predicate must filter on. Do not confuse it with {@link #studentCountryId},
     * which is the student's nationality and comes from a different lookup table
     * with overlapping ids — id 1 is Egypt here and Yemen there.
     */
    @Id
    @Column(name = "study_country_in_id")
    private Integer studyCountryInId;

    /** The student's home/nationality country. Report content, never the tenant key. */
    @Id
    @Column(name = "STUDENT_COUNTRY_ID")
    private Integer studentCountryId;
    
    @Id
    @Column(name = "STUDENT_MAJOR_ID")
    private Integer studentMajorId;
    
    @Column(name = "STUDENT_STUDY_STATUS_NAME")
    private String studentStudyStatusName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_STUDY_STATUS_NAME_EN")
    private String studentStudyStatusNameEn;
    
    @Column(name = "STUDENT_COUNTRY_NAME")
    private String studentCountryName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_COUNTRY_NAME_EN")
    private String studentCountryNameEn;
    
    @Column(name = "STUDENT_MAJOR_NAME")
    private String studentMajorName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_MAJOR_NAME_EN")
    private String studentMajorNameEn;
    
    @Column(name = "student_count")
    private Integer studentCount;
    
    @Column(name = "status_category")
    private String statusCategory;
    
    @Getter
    @Setter
    @lombok.EqualsAndHashCode
    public static class StudentStatusReportId implements Serializable {
        private Integer studentStudyStatusId;
        private Integer studyCountryInId;
        private Integer studentCountryId;
        private Integer studentMajorId;
    }
}

