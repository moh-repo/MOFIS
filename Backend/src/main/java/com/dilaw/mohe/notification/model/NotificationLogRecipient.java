package com.dilaw.mohe.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One person a batch went to, on one channel (V140): who, the address that
 * channel used, and whether it went. Written by the dispatcher with the batch's
 * outcome; read by the history page's view. Belongs to its {@link NotificationLog}
 * and carries no country of its own — the log's scope is the row's scope.
 */
@Getter
@Setter
@Entity
@Table(name = "NOTIFICATION_LOG_RECIPIENT", schema = "MOHECORE")
public class NotificationLogRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "LOG_ID", nullable = false)
    private Long logId;

    /** Codes of LU_NOTIFICATION_RECIPIENT_TYPE: STUDENT or EMPLOYEE. */
    @Column(name = "RECIPIENT_TYPE", nullable = false, length = 20)
    private String recipientType;

    /** STUDENT_DATA.STUDENT_ID or UMSCORE.Users.Id, by type. */
    @Column(name = "RECIPIENT_ID")
    private Long recipientId;

    @Column(name = "RECIPIENT_NAME", length = 300)
    private String recipientName;

    /** The e-mail or phone this channel used; null when the person had none on file. */
    @Column(name = "ADDRESS", length = 320)
    private String address;

    /** SENT or FAILED (codes of LU_NOTIFICATION_SEND_STATUS). */
    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "ERROR", length = 1000)
    private String error;

    @Column(name = "SENT_AT", nullable = false)
    private Instant sentAt;

    @PrePersist
    public void prePersist() {
        if (sentAt == null) sentAt = Instant.now();
    }
}
