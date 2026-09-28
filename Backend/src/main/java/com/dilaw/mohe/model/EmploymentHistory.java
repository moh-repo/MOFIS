package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "EMPLOYMENT_HISTORY", schema = "MOHENTracer")
public class EmploymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EMPLOYMENT_ID")
    private Integer employmentId;

    @Column(name = "NODE_ID", nullable = false)
    private Integer nodeId;

    @Nationalized
    @Column(name = "COMPANY_NAME", nullable = false, length = 255)
    private String companyName;

    @Nationalized
    @Column(name = "POSITION_TITLE", nullable = false, length = 255)
    private String positionTitle;

    @Nationalized
    @Column(name = "EMPLOYMENT_TYPE", length = 100)
    private String employmentType;

    @Nationalized
    @Column(name = "INDUSTRY", length = 100)
    private String industry;

    @Column(name = "START_DATE")
    private LocalDate startDate;

    @Column(name = "END_DATE")
    private LocalDate endDate;

    @Column(name = "IS_CURRENT")
    private Boolean isCurrent;

    @Nationalized
    @Column(name = "SALARY_RANGE", length = 100)
    private String salaryRange;

    @Nationalized
    @Column(name = "LOCATION", length = 255)
    private String location;

    @Nationalized
    @Column(name = "JOB_DESCRIPTION", columnDefinition = "NVARCHAR(MAX)")
    private String jobDescription;

    @Column(name = "CREATED_DATE")
    private LocalDateTime createdDate;

    @Nationalized
    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @Column(name = "MODIFIED_DATE")
    private LocalDateTime modifiedDate;

    @Nationalized
    @Column(name = "MODIFIED_BY", length = 100)
    private String modifiedBy;
}

