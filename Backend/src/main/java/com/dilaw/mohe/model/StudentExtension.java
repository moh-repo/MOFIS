package com.dilaw.mohe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_EXTENSION", schema = "MOHECORE")
public class StudentExtension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EXTENSION_ID")
    private Integer extensionId;

    @Column(name = "STUDENT_ID")
    private Integer studentId;

    @Nationalized
    @Column(name = "EXTENSION_TYPE")
    private String extensionType;

    @Column(name = "EXTENSION_QUARTERS")
    private Integer extensionQuarters;

    @Column(name = "EXTENSION_START_DATE")
    private LocalDate extensionStartDate;

    @Column(name = "EXTENSION_END_DATE")
    private LocalDate extensionEndDate;

    @Nationalized
    @Column(name = "EXTENSION_REASON")
    private String extensionReason;

    @Nationalized
    @Column(name = "EXTENSION_REASON_DETAILS")
    private String extensionReasonDetails;

    @Nationalized
    @Column(name = "EXTENSION_DECISION_NO")
    private String extensionDecisionNo;

    @Column(name = "EXTENSION_DECISION_DATE")
    private LocalDate extensionDecisionDate;

    @Nationalized
    @Column(name = "APPROVED_BY")
    private String approvedBy;

    @Nationalized
    @Column(name = "EXTENSION_STATUS")
    private String extensionStatus;

    @Column(name = "CREATED_DATE")
    private LocalDateTime createdDate;

    @Nationalized
    @Column(name = "CREATED_BY")
    private String createdBy;

    @Column(name = "MODIFIED_DATE")
    private LocalDateTime modifiedDate;

    @Nationalized
    @Column(name = "MODIFIED_BY")
    private String modifiedBy;

    @Nationalized
    @Column(name = "NOTES")
    private String notes;
}


