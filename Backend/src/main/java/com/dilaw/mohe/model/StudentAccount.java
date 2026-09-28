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
@Table(name = "STUDENT_ACCOUNT", schema = "MOHECORE")
public class StudentAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ColumnDefault("0")
    @Column(name = "STUDENT_ID", nullable = false)
    private Integer id;

    @ColumnDefault("0")
    @Column(name = "STUDY_FINANCE_BANKE_ID")
    private Integer studyFinanceBankeId;

    @ColumnDefault("0")
    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;

    @Nationalized
    @Column(name = "STUDENT_ACCOUNT")
    private String studentAccount;

    @Column(name = "STUDENT_ACCOUNT_START_DATE")
    private Instant studentAccountStartDate;

    @Column(name = "STUDENT_ACCOUNT_END_DATE")
    private Instant studentAccountEndDate;

    @ColumnDefault("0")
    @Column(name = "STUDENT_ACCOUNT_CURRENCY_ID")
    private Integer studentAccountCurrencyId;

    @ColumnDefault("0")
    @Column(name = "STUDENT_ACCOUNT_STATUS")
    private Integer studentAccountStatus;

}