package com.dilaw.mohe.accounting.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * An exchange rate on a given day: how many {@link #toCurrencyId} units one
 * {@link #fromCurrencyId} unit buys.
 *
 * <p>Dated, because a transaction may only be translated at the rate of the day
 * it happened. The pre-existing {@code STUDY_CURRENCY_IN_EXCHANGE} column looks
 * like a rate but holds 0, 1, 2 and 3 — USD 2, GBP 3, EUR 2, SAR 0 — so whatever
 * those are, no arithmetic on them produces a rate. Nothing here reads it.
 *
 * <p>Eight decimal places: a rate quoted to five and then inverted needs the
 * headroom, and rounding the rate before applying it pushes the error into every
 * translated amount.
 */
@Getter
@Setter
@Entity
@Table(name = "FX_RATE", schema = "FINCORE")
public class FxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FX_RATE_ID", nullable = false)
    private Integer id;

    /* Bean constraints mirror the columns (plan item 2.6); rateDate stays optional
     * because the service defaults it to today. */

    @NotNull(message = "fromCurrencyId is required")
    @Column(name = "FROM_CURRENCY_ID", nullable = false)
    private Integer fromCurrencyId;

    @NotNull(message = "toCurrencyId is required")
    @Column(name = "TO_CURRENCY_ID", nullable = false)
    private Integer toCurrencyId;

    @Column(name = "RATE_DATE", nullable = false)
    private LocalDate rateDate;

    @NotNull(message = "rate is required")
    @Positive(message = "A rate must be a positive number.")
    @Column(name = "RATE", nullable = false, precision = 18, scale = 8)
    private BigDecimal rate;

    /** Where the rate came from, e.g. the Central Bank of Yemen or an attache advice. */
    @Nationalized
    @Size(max = 50)
    @Column(name = "RATE_SOURCE", length = 50)
    private String rateSource;

    @Nationalized
    @Size(max = 500)
    @Column(name = "NOTES", length = 500)
    private String notes;

    @Column(name = "CREATED_BY")
    private Integer createdBy;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private Instant createdAt;
}
