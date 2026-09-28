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
@Table(name = "STUDY_FIN_RECEIVED_WHOM_STATUS", schema = "MOHECORE")
public class StudyFinReceivedWhomStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ColumnDefault("0")
    @Column(name = "STUDY_FIN_RECEIVED_WHOM_STATUS_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDY_FIN_RECEIVED_WHOM_STATUS_NAME")
    private String studyFinReceivedWhomStatusName;

    @Column(name = "STUDY_FIN_RECEIVED_WHOM_STATUS_NAME_EN")
    private String studyFinReceivedWhomStatusNameEn;

    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive = true;

    @ColumnDefault("getdate()")
    @Column(name = "STUDY_FIN_RECEIVED_WHOM_STATUS_MIDATE")
    private Instant studyFinReceivedWhomStatusMidate;

}