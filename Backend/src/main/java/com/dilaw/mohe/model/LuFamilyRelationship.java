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
@Table(name = "LU_FAMILY_RELATIONSHIP", schema = "MOHECORE")
public class LuFamilyRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REL_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "REL_CODE", nullable = false, length = 50)
    private String relCode;

    @Nationalized
    @Column(name = "REL_NAME", nullable = false, length = 200)
    private String relName;

    @Nationalized
    @Column(name = "REL_NAME_AR", length = 200)
    private String relNameAr;

    @ColumnDefault("0")
    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder = 0;

    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive = true;
}
