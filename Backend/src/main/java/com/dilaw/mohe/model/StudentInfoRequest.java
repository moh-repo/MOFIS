package com.dilaw.mohe.model;

import com.dilaw.mohe.codes.InfoRequestStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "StudentInfoRequest", schema = "MOHECORE")
public class StudentInfoRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id", nullable = false)
    private Integer id;

    @Column(name = "StudentId", nullable = false)
    private Integer studentId;

    @Nationalized
    @Column(name = "RequestType", nullable = false, length = 50)
    private String requestType;

    @Nationalized
    @Column(name = "Title", nullable = false, length = 255)
    private String title;

    @Nationalized
    @Column(name = "Description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "CreatedBy", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "CreatedAt", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "DueDate")
    private Instant dueDate;

    @Nationalized
    @Column(name = "Status", nullable = false, length = 50)
    private String status = InfoRequestStatus.PENDING;

    @Nationalized
    @Column(name = "ResponseText", columnDefinition = "NVARCHAR(MAX)")
    private String responseText;

    @Column(name = "ResponseAt")
    private Instant responseAt;

    @Column(name = "ResponseDocumentId")
    private Integer responseDocumentId;

    @Nationalized
    @Column(name = "ReviewStatus", length = 50)
    private String reviewStatus;

    @Column(name = "ReviewedBy")
    private Long reviewedBy;

    @Column(name = "ReviewedAt")
    private Instant reviewedAt;

    @Nationalized
    @Column(name = "RejectionReason", length = 500)
    private String rejectionReason;

    @Column(name = "ApprovalRequestId")
    private Long approvalRequestId;
}
