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
@Table(name = "STUDENT_QUARTER", schema = "MOHECORE")
public class StudentQuarter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ColumnDefault("0")
    @Column(name = "STUDY_FINANCE_DOF_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDY_FINANCE_DOF_NAME")
    private String studyFinanceDofName;

    @ColumnDefault("getdate()")
    @Column(name = "STUDY_FINANCE_DOF_MIDATE")
    private Instant studyFinanceDofMidate;

}