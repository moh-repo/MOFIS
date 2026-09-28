package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "STUDY_PAY_FEE_IN_STATUS", schema = "MOHECORE")
public class StudyPayFeeInStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ColumnDefault("0")
    @Column(name = "STUDY_PAY_FEE_IN_STATUS_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDY_PAY_FEE_IN_STATUS_NAME")
    private String studyPayFeeInStatusName;

    @Column(name = "STUDY_PAY_FEE_IN_STATUS_NAME_EN")
    private String studyPayFeeInStatusNameEn;

    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive = true;

}