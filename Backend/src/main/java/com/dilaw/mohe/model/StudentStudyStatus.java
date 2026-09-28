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
@Table(name = "STUDENT_STUDY_STATUS", schema = "MOHECORE")
public class StudentStudyStatus {
    /**
     * Not an IDENTITY column in SQL Server - the baseline schema declares it
     * {@code int NOT NULL DEFAULT ((0))} - so it must be assigned before insert;
     * the service sets it from {@code repository.nextId()}. Same situation as
     * {@link StudyFinanceBank}.
     */
    @Id
    @ColumnDefault("0")
    @Column(name = "STUDENT_STUDY_STATUS_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDENT_STUDY_STATUS_NAME")
    private String studentStudyStatusName;

    @Column(name = "STUDENT_STUDY_STATUS_NAME_EN")
    private String studentStudyStatusNameEn;

    /**
     * Stable key for this status (V028), e.g. ACTIVE, NEW, SUSPENDED.
     *
     * <p>Report code must key on this rather than on the ID or the display name.
     * The IDs are not what they look like — 2 is New, not Graduated, and 3/4 are
     * Withdrawn/Suspended rather than the reverse — and the names are
     * user-editable and language-specific.
     */
    @Column(name = "CODE", length = 40)
    private String code;

    /** No field initialiser — see {@link StudyFinanceBank#getIsActive()}; a new row is active unless stated (L1). */
    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @PrePersist
    void defaultActive() {
        if (isActive == null) isActive = true;
    }

    @ColumnDefault("getdate()")
    @Column(name = "STUDENT_STUDY_STATUS_MIDATE")
    private Instant studentStudyStatusMidate;

}