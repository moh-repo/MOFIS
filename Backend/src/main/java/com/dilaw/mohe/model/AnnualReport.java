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
@Table(name = "V_Annual_Report", schema = "MOHECORE")
@IdClass(AnnualReport.AnnualReportId.class)
public class AnnualReport {
    
    @Id
    @Column(name = "report_year")
    private Integer reportYear;

    @Id
    @Column(name = "study_country_id")
    private Integer countryId;
    
    @Column(name = "total_students")
    private Integer totalStudents;
    
    @Column(name = "new_enrollments")
    private Integer newEnrollments;
    
    @Column(name = "active_students")
    private Integer activeStudents;    
    @Column(name = "suspended_students")
    private Integer suspendedStudents;
    
    @Column(name = "total_financial_transactions")
    private Integer totalFinancialTransactions;
    
    @Column(name = "total_amount_disbursed")
    private BigDecimal totalAmountDisbursed;
    
    @Column(name = "scholarship_amount_disbursed")
    private BigDecimal scholarshipAmountDisbursed;
    
    @Column(name = "regular_amount_disbursed")
    private BigDecimal regularAmountDisbursed;
    
    @Column(name = "avg_amount_per_student")
    private BigDecimal avgAmountPerStudent;
    
    @Column(name = "max_amount_disbursed")
    private BigDecimal maxAmountDisbursed;
    
    @Column(name = "min_amount_disbursed")
    private BigDecimal minAmountDisbursed;
    
    @Column(name = "scholarship_students")
    private Integer scholarshipStudents;
    
    @Column(name = "regular_students")
    private Integer regularStudents;
    
    @Column(name = "male_students")
    private Integer maleStudents;
    
    @Column(name = "female_students")
    private Integer femaleStudents;
    
    @Column(name = "total_universities")
    private Integer totalUniversities;
    
    @Column(name = "total_countries")
    private Integer totalCountries;
    
    @Column(name = "total_majors")
    private Integer totalMajors;
    
    @Column(name = "total_degrees")
    private Integer totalDegrees;

    @Getter
    @Setter
    @lombok.EqualsAndHashCode
    public static class AnnualReportId implements java.io.Serializable {
        private Integer reportYear;
        private Integer countryId;
    }
}

