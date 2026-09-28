package com.dilaw.mohe.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "STUDY_CURRENCY_IN", schema = "MOHECORE")
public class StudyCurrencyIn {
    /**
     * Not an IDENTITY column in SQL Server - the baseline schema declares it
     * {@code int NOT NULL DEFAULT ((0))} - so it must be assigned before insert;
     * the service sets it from {@code repository.nextId()}. Same situation as
     * {@link StudyFinanceBank}.
     */
    @Id
    @ColumnDefault("0")
    @Column(name = "STUDY_CURRENCY_IN_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDY_CURRENCY_IN_NAME")
    private String studyCurrencyInName;

    @Column(name = "STUDY_CURRENCY_IN_NAME_EN")
    private String studyCurrencyInNameEn;

    /** No field initialiser — see {@link StudyFinanceBank#getIsActive()}; a new row is active unless stated (L1). */
    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @PrePersist
    void defaultActive() {
        if (isActive == null) isActive = true;
    }

    @ColumnDefault("0")
    @Column(name = "STUDY_CURRENCY_IN_EXCHANGE")
    private Integer studyCurrencyInExchange;

    @Column(name = "STUDY_CURRENCY_IN_MIDATE")
    private Instant studyCurrencyInMidate;

    /*
     * The three columns V039 added for the currency and FX foundation. They have been
     * in the table since, and unmapped here — so nothing in Java could read the
     * ministry's own currency, and the payment voucher printed the word "ريال" baked
     * into its label text instead.
     */

    /** ISO 4217, e.g. {@code YER}. Null on the legacy rows that predate V039. */
    @Column(name = "ISO_CODE", length = 3)
    @Size(max = 3)
    private String isoCode;

    /** Digits after the decimal point: 2 for the rial, 3 for the dinar. */
    @Column(name = "MINOR_UNITS")
    private Integer minorUnits;

    /**
     * The ministry's own currency — the one its books and its disbursement vouchers
     * are denominated in. {@code UX_STUDY_CURRENCY_IN_FUNCTIONAL} is a filtered unique
     * index on this column, so at most one row can carry it.
     */
    @ColumnDefault("0")
    @Column(name = "IS_FUNCTIONAL", nullable = false)
    private Boolean isFunctional = false;

}