package com.dilaw.mohe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_INFO_REQUEST_KIND", schema = "MOHECORE")
public class StudentInfoRequestKind {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "KIND_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "KIND_CODE", nullable = false, length = 80)
    private String kindCode;

    @Nationalized
    @Column(name = "DISPLAY_NAME", nullable = false, length = 200)
    private String displayName;

    @Nationalized
    @Column(name = "DISPLAY_NAME_AR", length = 200)
    private String displayNameAr;

    @ColumnDefault("0")
    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder = 0;

    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive = true;
}
