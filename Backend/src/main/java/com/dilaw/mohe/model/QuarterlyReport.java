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
@Table(name = "V_Quarterly_Report", schema = "MOHECORE")
@IdClass(QuarterlyReport.QuarterlyReportId.class)
public class QuarterlyReport {
    
    @Id
    @Column(name = "report_year")
    private Integer reportYear;
    
    @Id
    @Column(name = "report_quarter")
    private Integer reportQuarter;

    @Id
    @Column(name = "study_country_id")
    private Integer countryId;
    
    @Column(name = "report_period")
    private String reportPeriod;
    
    @Column(name = "total_students")
    private Integer totalStudents;
    
    @Column(name = "new_enrollments")
    private Integer newEnrollments;
    
    @Column(name = "active_students")
    private Integer activeStudents;    
    @Column(name = "total_financial_transactions")
    private Integer totalFinancialTransactions;
    
    @Column(name = "total_amount_disbursed")
    private BigDecimal totalAmountDisbursed;
    
    @Column(name = "scholarship_amount_disbursed")
    private BigDecimal scholarshipAmountDisbursed;
    
    @Column(name = "avg_amount_per_student")
    private BigDecimal avgAmountPerStudent;
    
    @Column(name = "scholarship_students")
    private Integer scholarshipStudents;
    
    @Column(name = "regular_students")
    private Integer regularStudents;
    
    @Column(name = "total_universities")
    private Integer totalUniversities;
    
    @Column(name = "total_countries")
    private Integer totalCountries;
    
    @Column(name = "total_majors")
    private Integer totalMajors;
    
    @Getter
    @Setter
    @lombok.EqualsAndHashCode
    public static class QuarterlyReportId implements java.io.Serializable {
        private Integer reportYear;
        private Integer reportQuarter;
        private Integer countryId;
    }
}

