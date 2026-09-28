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
@Table(name = "STUDENT_MINISTRY_SUB_TYPE", schema = "MOHECORE")
public class StudentMinistrySubType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ColumnDefault("0")
    @Column(name = "STUDENT_MINISTRY_SUB_TYPE_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDENT_MINISTRY_SUB_TYPE_NAME")
    private String studentMinistrySubTypeName;

    @Column(name = "STUDENT_MINISTRY_SUB_TYPE_NAME_EN")
    private String studentMinistrySubTypeNameEn;

    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive = true;

    @ColumnDefault("0")
    @Column(name = "STUDENT_MINISTRY_TYPE_ID")
    private Integer studentMinistryTypeId;

    @ColumnDefault("0")
    @Column(name = "STUDENT_AGENCY_TYPE_ID")
    private Integer studentAgencyTypeId;

    @ColumnDefault("getdate()")
    @Column(name = "STUDENT_MINISTRY_SUB_TYPE_MIDATE")
    private Instant studentMinistrySubTypeMidate;

}