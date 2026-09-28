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
@Table(name = "STUDY_FINANCE_BANK_BRANCH", schema = "MOHECORE")
public class StudyFinanceBankBranch {
    /**
     * Not an IDENTITY column in SQL Server — must be assigned before insert.
     */
    @Id
    @Column(name = "STUDY_FINANCE_BANK_BRANCH_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDY_FINANCE_BANK_BRANCH_NAME")
    private String studyFinanceBankBranchName;

    @Column(name = "STUDY_FINANCE_BANK_BRANCH_NAME_EN")
    private String studyFinanceBankBranchNameEn;

    /** No field initialiser — see {@link StudyFinanceBank#getIsActive()}. */
    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @ColumnDefault("0")
    @Column(name = "STUDY_FINANCE_BANKE_ID")
    private Integer studyFinanceBankeId;

    @ColumnDefault("0")
    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;

    @ColumnDefault("0")
    @Column(name = "STUDY_GOV_IN_ID")
    private Integer studyGovInId;

    @ColumnDefault("getdate()")
    @Column(name = "STUDY_FINANCE_BANK_BRANCH_MIDATE", insertable = false, updatable = false)
    private Instant studyFinanceBankBranchMidate;

}