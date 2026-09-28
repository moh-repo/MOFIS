package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "STUDY_FIN_RECEIVED_STATUS", schema = "MOHECORE")
public class StudyFinReceivedStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ColumnDefault("0")
    @Column(name = "STUDY_FIN_RECEIVED_STATUS_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDY_FIN_RECEIVED_STATUS_NAME")
    private String studyFinReceivedStatusName;

    @Column(name = "STUDY_FIN_RECEIVED_STATUS_NAME_EN")
    private String studyFinReceivedStatusNameEn;

    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive = true;

    /** Stable identifier for the status — RECEIVED, REJECTED, ON_HOLD, … */
    @Column(name = "CODE")
    private String code;

    /**
     * The PrimeNG severity the status is painted with, as the table states it.
     *
     * <p>The column has held a value for all nine statuses since V-May-2026, but
     * the entity never mapped it, so screens fell back to guessing from the id —
     * the recipients screen painted id 1 green and the other eight grey, which
     * made <em>Rejected</em> look exactly like <em>Not Received</em>. Reading the
     * column is what the dashboard and the accounting screens already do.
     */
    @Column(name = "UI_SEVERITY")
    private String uiSeverity;

    @ColumnDefault("getdate()")
    @Column(name = "STUDY_FIN_RECEIVED_STATUS_MIDATE")
    private Instant studyFinReceivedStatusMidate;

}