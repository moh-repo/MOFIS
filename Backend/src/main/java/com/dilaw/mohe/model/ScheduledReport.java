package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Immutable
@Table(name = "V_Scheduled_Report", schema = "MOHECORE")
public class ScheduledReport {
    
    @Id
    @Column(name = "schedule_id")
    private Long scheduleId;
    
    @Column(name = "schedule_name")
    private String scheduleName;
    
    @Column(name = "schedule_description")
    private String scheduleDescription;
    
    @Column(name = "frequency")
    private String frequency;
    
    @Column(name = "next_run_date")
    private LocalDateTime nextRunDate;
    
    @Column(name = "last_run_date")
    private LocalDateTime lastRunDate;
    
    @Column(name = "schedule_status")
    private String scheduleStatus;
    
    @Column(name = "records_in_last_run")
    private Integer recordsInLastRun;
    
    @Column(name = "recipient_email")
    private String recipientEmail;
    
    @Column(name = "is_enabled")
    private Integer isEnabled;
}

