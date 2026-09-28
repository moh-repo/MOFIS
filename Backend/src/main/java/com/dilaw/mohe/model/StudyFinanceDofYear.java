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
@Table(name = "STUDY_FINANCE_DOF_YEAR", schema = "MOHECORE")
public class StudyFinanceDofYear {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ColumnDefault("0")
    @Column(name = "STUDY_FINANCE_DOF_YEAR_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDY_FINANCE_DOF_YEAR_NAME")
    private String studyFinanceDofYearName;

    @Column(name = "STUDY_FINANCE_DOF_YEAR_NAME_EN")
    private String studyFinanceDofYearNameEn;

    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive = true;

    @ColumnDefault("getdate()")
    @Column(name = "STUDY_FINANCE_DOF_YEAR_MIDATE")
    private Instant studyFinanceDofYearMidate;

}