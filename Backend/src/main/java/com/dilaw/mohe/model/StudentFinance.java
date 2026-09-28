package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Nationalized;
import org.hibernate.generator.EventType;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_FINANCE", schema = "MOHECORE")
public class StudentFinance {
    @Id
    @ColumnDefault("0")
    @Column(name = "STUDENT_ID", nullable = false)
    @JsonProperty("id")
    @JsonAlias({ "studentId", "student_id", "STUDENT_ID" })
    private Integer id;

    @Nationalized
    @Column(name = "STUDENT_FIN_ID")
    private String studentFinId;

    @Nationalized
    @Column(name = "STUDENT_FIN_NAME")
    private String studentFinName;

    @ColumnDefault("0")
    @Column(name = "STUDENT_FIN_RECIEVED_YEAR")
    private Integer studentFinRecievedYear;

    @ColumnDefault("0")
    @Column(name = "STUDENT_FIN_RECIEVED_MONTH")
    private Integer studentFinRecievedMonth;

    /**
     * Period key in {@code YYYYQ} form (20242 = 2024 quarter 2), <em>not</em> a calendar
     * year-month despite the column name. Always {@code STUDENT_FIN_RECIEVED_YEAR * 10 +
     * STUDENT_FIN_RECIEVED_MONTH} where MONTH holds the quarter number 1-4: the live
     * table, STUDENT_FINANCE_ARCH, STUDENT_FINANCE_WAFAR and the payment ledger all
     * carry that form, V038 checks the identity, and FINCORE.ATTACHE_ALLOCATION keys
     * on it. Written by {@code MoheQuarterCreateService} and
     * {@code StudentFinanceService}; read through {@code QuarterFinanceTotalsService#quarterKey}.
     */
    @ColumnDefault("0")
    @Column(name = "STUDENT_FIN_RECIEVED_YEAR_MONTH")
    private Integer studentFinRecievedYearMonth;

    @ColumnDefault("0")
    @Column(name = "STUDENT_FIN_RECIEVED_AMOUNT")
    private Integer studentFinRecievedAmount;

    @Column(name = "STUDY_CURRENCY_IN_ID")
    private Integer studyCurrencyInId;

    @ColumnDefault("0")
    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;

    @ColumnDefault("0")
    @Column(name = "STUDY_GOV_IN_ID")
    private Integer studyGovInId;

    @ColumnDefault("0")
    @Column(name = "STUDY_FINANCE_BANKE_ID")
    private Integer studyFinanceBankeId;

    @ColumnDefault("0")
    @Column(name = "STUDY_FINANCE_BANK_BRANCH_ID")
    private Integer studyFinanceBankBranchId;

    @ColumnDefault("0")
    @Column(name = "STUDY_FINANCE_DOF_ID")
    private Integer studyFinanceDofId;

    @Nationalized
    @Column(name = "STUDY_CHECK_ID")
    private String studyCheckId;

    @Column(name = "STUDY_CHECK_DATE")
    private Instant studyCheckDate;

    @Nationalized
    @Lob
    @Column(name = "STUDY_CHECK_DETAILS")
    private String studyCheckDetails;

    @Nationalized
    @Column(name = "STUDY_CHECK_FORM_NUMBER")
    private String studyCheckFormNumber;

    @ColumnDefault("0")
    @Column(name = "STUDY_FIN_RECIEVED_STATUS")
    private Integer studyFinRecievedStatus;

    @Nationalized
    @Lob
    @Column(name = "STUDY_NOTE_1")
    private String studyNote1;

    @ColumnDefault("0")
    @Column(name = "STUDY_RECEIVED_WHOM_STATUS_ID")
    private Integer studyReceivedWhomStatusId;

    @ColumnDefault("0")
    @Column(name = "STUDY_RECEIVED_WHOM_ID")
    private Integer studyReceivedWhomId;

    @ColumnDefault("0")
    @Column(name = "WAFAR_AMOUNT")
    private Integer wafarAmount;

    @ColumnDefault("0")
    @Column(name = "WAFAR_TYPE_ID")
    private Integer wafarTypeId;

    /**
     * What the student actually nets: STUDENT_FIN_RECIEVED_AMOUNT - WAFAR_AMOUNT.
     *
     * A PERSISTED computed column in the database (V015), so nothing here ever writes
     * it. {@code @Generated} keeps it out of the INSERT/UPDATE column list and makes
     * Hibernate re-read it after a write, so an entity returned straight from save()
     * carries the new net rather than a stale value.
     *
     * READ_ONLY for Jackson is load-bearing, not cosmetic: this entity is round-tripped
     * through the approval payload (writeValueAsString -> requestData -> readValue), so
     * without it a caller could post a netAmount that contradicts the amount and wafar.
     */
    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "NET_AMOUNT")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer netAmount;

    /**
     * Wafar note entered on the finance form. STUDENT_FINANCE has no column for it —
     * it belongs to STUDENT_FINANCE_WAFAR.STUDY_NOTE_1 — but the wafar row is only
     * written once the finance request is approved, so the note has to travel inside
     * the approval payload to reach the executor. Transient for JPA, serialized by
     * Jackson.
     */
    @Transient
    private String wafarNote;

    @ColumnDefault("getdate()")
    @Column(name = "STUDY_FINANCE_MIDATE")
    private Instant studyFinanceMidate;

    @ColumnDefault("0")
    @Column(name = "STUDENT_BANK_ID")
    private Integer studentBankId;

    /** O-03: the bank account is masked for anyone outside finance; R-18 lets the catalogue refine it. */
    @com.dilaw.mohe.config.FieldPermission(page = "FIN_SCHOLARSHIPS", field = "BANK_ACCOUNT",
            anyOf = {"FINANCE.VIEW", "FINANCE.EDIT", "UMS_ADMIN.VIEW"}, editAnyOf = {"FINANCE.EDIT"})
    @Nationalized
    @Column(name = "STUDENT_ACCOUNT_NUMBER")
    private String studentAccountNumber;

    @Nationalized
    @Column(name = "STUDENT_ACCOUNT_ENGLISH_NAME")
    private String studentAccountEnglishName;

    @Nationalized
    @Column(name = "STUDENT_ACCOUNT_END_DATE")
    private String studentAccountEndDate;

    @Nationalized
    @Column(name = "STUDENT_BANK_NOTES")
    private String studentBankNotes;

    @Column(name = "MOHE_QUARTER_ID")
    private Integer moheQuarterId;

}