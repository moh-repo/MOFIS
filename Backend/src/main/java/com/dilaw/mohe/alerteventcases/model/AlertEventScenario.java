package com.dilaw.mohe.alerteventcases.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "ALERT_EVENT_SCENARIO", schema = "ALERTEVENTCASES")
public class AlertEventScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SCENARIO_ID", nullable = false)
    private Integer scenarioId;

    @Nationalized
    @Column(name = "SCENARIO_CODE", nullable = false, unique = true, length = 50)
    private String scenarioCode;

    @Nationalized
    @Column(name = "SCENARIO_NAME_EN", nullable = false, length = 200)
    private String scenarioNameEn;

    @Nationalized
    @Column(name = "SCENARIO_NAME_AR", nullable = false, length = 200)
    private String scenarioNameAr;

    @Nationalized
    @Lob
    @Column(name = "DESCRIPTION_EN")
    private String descriptionEn;

    @Nationalized
    @Lob
    @Column(name = "DESCRIPTION_AR")
    private String descriptionAr;

    @Nationalized
    @ColumnDefault("'MEDIUM'")
    @Column(name = "SEVERITY", nullable = false, length = 20)
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(name = "THRESHOLD_DAYS")
    private Integer thresholdDays;

    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive = true;

    @ColumnDefault("getdate()")
    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;
}
