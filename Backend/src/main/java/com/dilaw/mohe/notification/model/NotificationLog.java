package com.dilaw.mohe.notification.model;

import com.dilaw.mohe.codes.NotificationSendStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "NOTIFICATION_LOG", schema = "MOHECORE")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "BATCH_ID", nullable = false, length = 36)
    private String batchId;

    @Column(name = "CHANNEL", nullable = false, length = 10)
    @Size(max = 10)
    private String channel; // EMAIL | SMS

    @Column(name = "RECIPIENT_TYPE", nullable = false, length = 20)
    private String recipientType; // STUDENT | EMPLOYEE

    @Column(name = "SCOPE", nullable = false, length = 10)
    @Size(max = 10)
    private String scope; // ONE | GROUP | ALL

    @Column(name = "SUBJECT", length = 500)
    private String subject;

    @Column(name = "BODY", columnDefinition = "NVARCHAR(MAX)", nullable = false)
    private String body;

    @Column(name = "TOTAL_COUNT", nullable = false)
    private Integer totalCount = 0;

    @Column(name = "SENT_COUNT", nullable = false)
    private Integer sentCount = 0;

    @Column(name = "FAILED_COUNT", nullable = false)
    private Integer failedCount = 0;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status = NotificationSendStatus.PENDING; // codes of LU_NOTIFICATION_SEND_STATUS

    @Column(name = "ERROR_SUMMARY", columnDefinition = "NVARCHAR(MAX)")
    private String errorSummary;

    @Column(name = "CREATED_BY")
    private Integer createdBy;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "SENT_AT")
    private Instant sentAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
