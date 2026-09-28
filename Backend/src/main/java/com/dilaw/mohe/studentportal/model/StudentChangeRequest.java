package com.dilaw.mohe.studentportal.model;

import com.dilaw.mohe.codes.ChangeRequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "student_change_requests", schema = "STUDENTCORE")
public class StudentChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Integer requestId;

    @Column(name = "student_id", nullable = false)
    private Integer studentId;

    @Column(name = "student_user_id", nullable = false)
    private Integer studentUserId;

    @Column(name = "submission_id", nullable = false, length = 50)
    private String submissionId;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "field_label", length = 200)
    private String fieldLabel;

    @Column(name = "old_value", columnDefinition = "NVARCHAR(MAX)")
    private String oldValue;

    @Column(name = "new_value", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String newValue;

    @Column(name = "status", nullable = false, length = 20)
    private String status = ChangeRequestStatus.PENDING;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "employee_comment", length = 500)
    private String employeeComment;

    @Column(name = "applied_value", columnDefinition = "NVARCHAR(MAX)")
    private String appliedValue;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
