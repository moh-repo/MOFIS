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
@Table(name = "LU_TRAVEL_TYPE", schema = "MOHECORE")
public class LuTravelType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TRAVEL_TYPE_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "TYPE_CODE", nullable = false, length = 50)
    private String typeCode;

    @Nationalized
    @Column(name = "TYPE_NAME", nullable = false, length = 200)
    private String typeName;

    @Nationalized
    @Column(name = "TYPE_NAME_AR", length = 200)
    private String typeNameAr;

    @ColumnDefault("0")
    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder = 0;

    @ColumnDefault("1")
    @Column(name = "ACTIVE", nullable = false)
    private Boolean active = true;
}
