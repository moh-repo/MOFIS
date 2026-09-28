package com.dilaw.mohe.model;

import com.dilaw.mohe.codes.ScheduleStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

/**
 * Writable entity for the MOHECORE.SCHEDULED_REPORT table.
 * (The legacy ScheduledReport entity maps to the V_Scheduled_Report VIEW and is read-only.)
 */
@Getter
@Setter
@Entity
@Table(name = "SCHEDULED_REPORT", schema = "MOHECORE")
public class ScheduledReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SCHEDULE_ID")
    private Integer scheduleId;

    @Nationalized
    @Column(name = "SCHEDULE_NAME", nullable = false, length = 200)
    private String scheduleName;

    @Nationalized
    @Column(name = "SCHEDULE_DESCRIPTION", length = 500)
    private String scheduleDescription;

    @Column(name = "REPORT_TYPE", nullable = false, length = 100)
    private String reportType = "STUDENT_LIST";

    /** DAILY, WEEKLY, MONTHLY */
    @Column(name = "FREQUENCY", nullable = false, length = 50)
    private String frequency = "WEEKLY";

    @Column(name = "CRON_EXPRESSION", length = 100)
    private String cronExpression;

    @Column(name = "NEXT_RUN_DATE")
    private Instant nextRunDate;

    @Column(name = "LAST_RUN_DATE")
    private Instant lastRunDate;

    @Column(name = "LAST_RUN_STATUS", length = 50)
    private String lastRunStatus;

    @Column(name = "RECORDS_IN_LAST_RUN")
    private Integer recordsInLastRun;

    @Nationalized
    @Column(name = "RECIPIENT_EMAIL", length = 500)
    private String recipientEmail;

    /** ACTIVE, PAUSED, DISABLED */
    @Column(name = "SCHEDULE_STATUS", nullable = false, length = 30)
    private String scheduleStatus = ScheduleStatus.ACTIVE;

    @Column(name = "IS_ENABLED", nullable = false)
    private Boolean isEnabled = true;

    @Column(name = "CREATED_DATE")
    private Instant createdDate;

    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @Column(name = "MODIFIED_DATE")
    private Instant modifiedDate;

    @Column(name = "MODIFIED_BY", length = 100)
    private String modifiedBy;

    @PrePersist
    void onPersist() {
        if (createdDate == null) createdDate = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        modifiedDate = Instant.now();
    }
}
