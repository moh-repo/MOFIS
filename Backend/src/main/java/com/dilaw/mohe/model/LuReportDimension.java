package com.dilaw.mohe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "LU_REPORT_DIMENSION", schema = "MOHECORE")
public class LuReportDimension extends AbstractLuLookup {

    @Nationalized
    @Column(name = "GROUP_CODE", nullable = false, length = 50)
    private String groupCode;
}
