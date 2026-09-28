package com.dilaw.mohe.model;

import com.dilaw.mohe.codes.NoticeStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "alerts", schema = "MOHE_ALERTS")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_id", nullable = false)
    private Integer alertId;

    @Nationalized
    @Column(name = "alert_title")
    private String alertTitle;

    @Nationalized
    @Lob
    @Column(name = "alert_message")
    private String alertMessage;

    /** The English text, where the notice has one (V125); the screens fall back to the Arabic. */
    @Column(name = "alert_title_en")
    private String alertTitleEn;

    @Column(name = "alert_message_en")
    private String alertMessageEn;

    /** What raised the notice, e.g. APPROVAL:20732, so it can be resolved when that is decided (V125). */
    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "alert_type")
    private String alertType; // codes of LU_ALERT_TYPE

    @Column(name = "alert_priority")
    private String alertPriority; // codes of LU_ALERT_SEVERITY

    @Column(name = "alert_status")
    private String alertStatus; // codes of LU_NOTICE_STATUS

    @Column(name = "created_by")
    private String createdBy;

    @ColumnDefault("getdate()")
    @Column(name = "created_date")
    private Instant createdDate;

    @Column(name = "expires_date")
    private Instant expiresDate;

    /**
     * Whether the account asking has read this alert. Not a column (live QA N10): the
     * table's is_read / read_date / read_by were one flag for everybody, set under a
     * name the request supplied. Reads are per account in {@code alert_reads} (V144),
     * and {@code AlertService} fills this for the caller; the old columns are no longer
     * mapped.
     */
    @Transient
    private Boolean isRead = false;

    // Constructors
    public Alert() {
    }

    public Alert(String alertTitle, String alertMessage, String alertType, String alertPriority) {
        this.alertTitle = alertTitle;
        this.alertMessage = alertMessage;
        this.alertType = alertType;
        this.alertPriority = alertPriority;
        this.alertStatus = NoticeStatus.ACTIVE;
        this.isRead = false;
    }

    // How a type or priority is shown (icon, colour) is the code list's
    // (LU_ALERT_TYPE, LU_ALERT_SEVERITY) since V072/V073 — not a switch here.
}
