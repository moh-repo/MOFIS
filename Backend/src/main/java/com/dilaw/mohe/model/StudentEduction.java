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
@Table(name = "STUDENT_EDUCTION", schema = "MOHECORE")
public class StudentEduction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ColumnDefault("0")
    @Column(name = "STUD_EDUCTION_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUD_EDUCTION_NAME")
    private String studEductionName;

    @Column(name = "STUD_EDUCTION_NAME_EN")
    private String studEductionNameEn;

    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive = true;

    @ColumnDefault("getdate()")
    @Column(name = "STUD_EDUCTION_MIDATE")
    private Instant studEductionMidate;

}