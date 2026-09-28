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
@Table(name = "LU_TREND_CLASS", schema = "MOHECORE")
public class LuTrendClass extends AbstractLuLookup {

    @Nationalized
    @Column(name = "CATEGORY", nullable = false, length = 50)
    private String category;
}
