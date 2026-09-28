package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "STUDY_RECEIVED_WHOM_STATUS", schema = "MOHECORE")
public class StudyReceivedWhomStatus {
    /**
     * Not an IDENTITY column in SQL Server - the baseline schema declares it
     * {@code int NOT NULL DEFAULT ((0))} - so it must be assigned before insert;
     * the service sets it from {@code repository.nextId()}. Same situation as
     * {@link StudyFinanceBank}.
     */
    @Id
    @ColumnDefault("0")
    @Column(name = "STUDY_RECEIVED_WHOM_STATUS_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDY_RECEIVED_WHOM_STATUS_NAME")
    private String studyReceivedWhomStatusName;

    @Column(name = "STUDY_RECEIVED_WHOM_STATUS_NAME_EN")
    private String studyReceivedWhomStatusNameEn;

    /** No field initialiser — see {@link StudyFinanceBank#getIsActive()}; a new row is active unless stated (L1). */
    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @PrePersist
    void defaultActive() {
        if (isActive == null) isActive = true;
    }

}