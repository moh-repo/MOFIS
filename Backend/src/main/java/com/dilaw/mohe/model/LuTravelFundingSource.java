package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/**
 * Lookup of funding sources for student travel tickets (Wafar / Ministry / Other, admin-editable).
 * The {@code fundingSourceCode} is the stable key the backend uses to detect Wafar-funded tickets.
 */
@Getter
@Setter
@Entity
@Table(name = "LU_TRAVEL_FUNDING_SOURCE", schema = "MOHECORE")
public class LuTravelFundingSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FUNDING_SOURCE_ID")
    private Integer fundingSourceId;

    @Column(name = "FUNDING_SOURCE_CODE", nullable = false, unique = true, length = 50)
    private String fundingSourceCode;

    @Column(name = "FUNDING_SOURCE_NAME_EN", nullable = false, length = 200)
    private String fundingSourceNameEn;

    @Nationalized
    @Column(name = "FUNDING_SOURCE_NAME_AR", length = 200)
    private String fundingSourceNameAr;

    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @Column(name = "DISPLAY_ORDER")
    private Integer displayOrder;
}
