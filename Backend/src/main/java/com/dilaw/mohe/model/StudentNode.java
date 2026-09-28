package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_NODE", schema = "MOHENTracer")
public class StudentNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NODE_ID")
    private Integer nodeId;

    @Column(name = "STUDENT_ID", nullable = false, unique = true)
    private Integer studentId;

    @Nationalized
    @Column(name = "STUDENT_NAME", nullable = false, length = 255)
    private String studentName;

    @Nationalized
    @Column(name = "STUDENT_NAME_AR", length = 255)
    private String studentNameAr;

    @Nationalized
    @Column(name = "UNIVERSITY", length = 255)
    private String university;

    @Nationalized
    @Column(name = "MAJOR", length = 255)
    private String major;

    @Column(name = "GRADUATION_YEAR")
    private Integer graduationYear;

    @Nationalized
    @Column(name = "DEGREE_LEVEL", length = 100)
    private String degreeLevel;

    @Nationalized
    @Column(name = "COUNTRY", length = 100)
    private String country;

    @Nationalized
    @Column(name = "CURRENT_EMPLOYMENT_STATUS", length = 100)
    private String currentEmploymentStatus;

    @Nationalized
    @Column(name = "CURRENT_POSITION", length = 255)
    private String currentPosition;

    @Nationalized
    @Column(name = "CURRENT_COMPANY", length = 255)
    private String currentCompany;

    @Column(name = "LINKEDIN_URL", length = 500)
    private String linkedinUrl;

    @Column(name = "EMAIL", length = 255)
    private String email;

    @Column(name = "PHONE", length = 50)
    private String phone;

    @Nationalized
    @Column(name = "NODE_TYPE", length = 50)
    private String nodeType;

    @Column(name = "NODE_COLOR", length = 50)
    private String nodeColor;

    @Column(name = "NODE_SIZE")
    private Integer nodeSize;

    @Column(name = "LATITUDE", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "LONGITUDE", precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

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

