package com.dilaw.mohe.wafar.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "WAFAR_SPENDING_CATEGORY", schema = "MOHECORE")
public class WafarSpendingCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CATEGORY_ID", nullable = false)
    private Integer id;

    @Column(name = "CATEGORY_CODE", nullable = false, length = 50)
    private String categoryCode;

    @Nationalized
    @Column(name = "CATEGORY_NAME_AR", nullable = false, length = 255)
    private String categoryNameAr;

    @Column(name = "CATEGORY_NAME_EN", nullable = false, length = 255)
    private String categoryNameEn;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive = true;
}
