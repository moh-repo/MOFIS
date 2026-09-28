package com.dilaw.mohe.alerteventcases.model;

import com.dilaw.mohe.codes.AlertCaseStatus;

import com.dilaw.mohe.model.StudentDatum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "ALERT_EVENT_CASE", schema = "ALERTEVENTCASES")
public class AlertEventCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CASE_ID", nullable = false)
    private Integer caseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SCENARIO_ID", nullable = false)
    private AlertEventScenario scenario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STUDENT_ID", nullable = false)
    private StudentDatum student;

    /**
     * Tenant key: denormalised from STUDENT_DATA.STUDY_COUNTRY_IN, which is what
     * UMSCORE.UserCountries holds and what country scope is checked against.
     *
     * <p>The underlying column is named STUDENT_COUNTRY_ID, which is a misnomer —
     * it does not hold STUDENT_DATA.STUDENT_COUNTRY_ID (the student's
     * nationality). The two are different lookup tables with overlapping ids, so
     * the Java field is named for what it actually contains; renaming the column
     * itself would need its own migration.
     */
    @Column(name = "STUDENT_COUNTRY_ID")
    private Integer studyCountryInId;

    @Column(name = "REFERENCE_DATE")
    private Instant referenceDate;

    @Nationalized
    @ColumnDefault("'OPEN'")
    @Column(name = "STATUS", nullable = false, length = 20)
    private String status = AlertCaseStatus.OPEN; // codes of LU_ALERT_STATUS

    @Nationalized
    @Lob
    @Column(name = "NOTES")
    private String notes;

    @Nationalized
    @Column(name = "ACKNOWLEDGED_BY", length = 150)
    private String acknowledgedBy;

    @Column(name = "ACKNOWLEDGED_AT")
    private Instant acknowledgedAt;

    @Nationalized
    @Column(name = "RESOLVED_BY", length = 150)
    private String resolvedBy;

    @Column(name = "RESOLVED_AT")
    private Instant resolvedAt;

    @ColumnDefault("0")
    @Column(name = "IS_DELETED", nullable = false)
    private Boolean isDeleted = false;

    @ColumnDefault("getdate()")
    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;
}
