package com.dilaw.mohe.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "MOHE_QUARTER", schema = "MOHECORE")
public class MoheQuarter {
    /**
     * The IDENTITY column in SQL Server — auto-generated on insert, and mapped as the
     * JPA id because it is the one column that is guaranteed to be there before the
     * row is.
     *
     * <p>It is <b>not</b> the database's primary key. {@code pk_MOHE_QUARTER} is on
     * {@link #quarter}, {@code ID} carries only a non-unique index, and every foreign
     * key that points at a quarter — {@code STUDENT_FINANCE.MOHE_QUARTER_ID},
     * {@code PAYMENT_BATCH}, {@code WAFAR_EARMARK}, {@code WAFAR_USE},
     * {@code ATTACHE_ALLOCATION} — references {@code QUARTER}. Anything that stores a
     * reference to a quarter stores that, whatever the column is called.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Integer id;

    /**
     * The {@code YYYYQ} period key — 20242 is 2024 Q2. The value users see and identify
     * a quarter by, and the form every other table carries the period in
     * ({@code STUDENT_DATA.CURRENT_QUARTER}, {@code STUDENT_FIN_RECIEVED_YEAR_MONTH},
     * the Wafar rows, the allocation join).
     *
     * <p>Derived from {@link #quarterYear} and {@link #quarterNumber} by
     * {@code MoheQuarterCreateService} and {@code MoheQuarterService.update}, never
     * accepted from a request body: the column is NOT NULL, the screen has no control
     * for it and never sent one, so a create from the screen inserted NULL into it.
     * READ_ONLY makes that impossible rather than merely unlikely.
     */
    @Column(name = "QUARTER", nullable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer quarter;

    /** Gregorian / fiscal year displayed with the MOHE quarter (e.g. 2026). */
    @Column(name = "QUARTER_YEAR")
    private Integer quarterYear;

    /** Quarter within the year: 1..4 only (semester-ish trimesters). */
    @Column(name = "QUARTER_NO")
    private Integer quarterNumber;

    @Nationalized
    @Column(name = "QUARTER_NAME")
    private String quarterName;

    @Column(name = "QUARTER_NAME_EN")
    private String quarterNameEn;

    /**
     * The quarter's total entitlement, summed from the student finance rows on
     * every read by {@code QuarterFinanceTotalsService} — see that class for which
     * table each quarter is read from.
     *
     * <p>The column behind it is legacy. It was typed in by hand, held {@code 0} on
     * all twelve rows, and is no longer written: {@code MoheQuarterService.update}
     * ignores whatever the client sends here, so an edit cannot overwrite a figure
     * that came out of the finance tables. It stays mapped so the old value can
     * still be read out of the database if anyone ever needs it.
     */
    @ColumnDefault("0")
    @Column(name = "QUARTER_AMOUNT")
    private Integer quarterAmount;

    /** Of {@link #quarterAmount}, the part that was not disbursed and became Wafar. Derived. */
    @Transient
    private Long quarterWafarAmount;

    /** {@code quarterAmount - quarterWafarAmount}: what actually reached students. Derived. */
    @Transient
    private Long quarterNetAmount;

    /** Finance rows behind the derived figures. Derived. */
    @Transient
    private Integer quarterStudentCount;

    /**
     * {@code LIVE}, {@code ARCHIVE}, or {@code null} when no finance rows exist for
     * the quarter. Which table the figures were summed from — worth showing,
     * because the two do not always agree.
     */
    @Transient
    private String quarterAmountSource;

    @Column(name = "QUARTER_PAY_DATE")
    private Instant quarterPayDate;

    @Column(name = "QUARTER_RECIVED_DATE")
    private Instant quarterRecivedDate;

    @Column(name = "INSERTED_DATE", insertable = false, updatable = false)
    private Instant insertedDate;

    @Nationalized
    @Column(name = "NOTES")
    private String notes;

    @ColumnDefault("0")
    @Column(name = "QuarterClosed", nullable = false)
    private Boolean quarterClosed = false;

    /** Total approved budget for this quarter (in the default currency). NULL = no budget cap. */
    @ColumnDefault("0")
    @Column(name = "BUDGET_AMOUNT")
    private Long budgetAmount;

    /** Running total of payments already authorized against this quarter's budget. */
    @ColumnDefault("0")
    @Column(name = "PAID_AMOUNT")
    private Long paidAmount = 0L;

    /**
     * Country this quarter belongs to. NULL = global quarter visible to all users.
     * Non-null = visible only to users whose scope includes this country (and ministry users).
     */
    @Column(name = "COUNTRY_ID")
    private Integer countryId;

}