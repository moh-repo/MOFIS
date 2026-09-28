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
@Table(name = "STUDENT_COUNTRY_ALL", schema = "MOHECORE")
public class StudentCountryAll {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ColumnDefault("0")
    @Column(name = "STUDENT_COUNTRY_ALL_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDENT_COUNTRY_ALL_NAME")
    private String studentCountryAllName;

    @Column(name = "STUDENT_COUNTRY_ALL_NAME_EN")
    private String studentCountryAllNameEn;

    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive = true;

    @ColumnDefault("0")
    @Column(name = "STUDENT_COUNTRY_ALL_ORDER_NO")
    private Integer studentCountryAllOrderNo;

    @ColumnDefault("0")
    @Column(name = "STUDENT_COUNTRY_ALL_STATUS")
    private Integer studentCountryAllStatus;

    @ColumnDefault("getdate()")
    @Column(name = "STUDENT_COUNTRY_ALL_INSERTED_DATE")
    private Instant studentCountryAllInsertedDate;

}