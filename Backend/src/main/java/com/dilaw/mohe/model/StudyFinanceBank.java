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
@Table(name = "STUDY_FINANCE_BANK", schema = "MOHECORE")
public class StudyFinanceBank {
    /**
     * Not an IDENTITY column in SQL Server — must be assigned before insert.
     * Use {@code SELECT ISNULL(MAX(STUDY_FINANCE_BANKE_ID), 0) + 1} or a
     * service-level sequence to assign.
     */
    @Id
    @Column(name = "STUDY_FINANCE_BANKE_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDY_FINANCE_BANKE_NAME")
    private String studyFinanceBankeName;

    @Column(name = "STUDY_FINANCE_BANKE_NAME_EN")
    private String studyFinanceBankeNameEn;

    /**
     * No field initialiser on purpose. This entity is bound straight from the
     * request body, and an initialiser makes an omitted flag indistinguishable
     * from an explicit {@code true} — which is how an edit that never mentions
     * IS_ACTIVE used to switch a disabled bank back on. Null means "not stated":
     * {@code create} defaults it, {@code update} leaves the stored value alone.
     */
    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @ColumnDefault("0")
    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;

    @ColumnDefault("getdate()")
    @Column(name = "STUDY_FINANCE_BANK_MIDATE", insertable = false, updatable = false)
    private Instant studyFinanceBankMidate;

}