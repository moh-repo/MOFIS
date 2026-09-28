package com.dilaw.mohe.accounting.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The advance (العهدة) a cultural attaché holds, per fiscal year and currency.
 *
 * <p>Money transferred to an attaché is still the ministry's until it is
 * disbursed. This row is the running account of it:
 *
 * <pre>
 *   AVAILABLE = OPENING + TRANSFERRED - DISBURSED - RETURNED
 *               - (WAFAR_EARMARKED - WAFAR_SPENT)
 * </pre>
 *
 * <p>{@code available} is un-earmarked cash and may never go negative — that
 * would be paying out money the attaché never held. At quarter close every
 * unpaid entitlement is earmarked to Wafar, so it reaches zero and the identity
 * {@code transferred − disbursed − wafar − returned = 0} holds exactly.
 * Mid-quarter it is positive, which is why it is modelled rather than assumed nil.
 *
 * <p><b>Spending Wafar moves two counters.</b> {@code wafarSpent} rises because
 * the earmark is released, and {@code disbursed} rises because the cash actually
 * leaves. {@code available} is therefore unchanged by a Wafar payment, which is
 * the right answer: paying a student out of savings does not make the attaché
 * any freer than it was. Move only one of the two and the balance drifts by the
 * value of every Wafar payment ever made — see
 * {@link com.dilaw.mohe.accounting.service.AdvanceBalanceService#recordWafarUse}.
 *
 * <p>{@code wafarBalance} and {@code available} are computed columns maintained
 * by SQL Server, so they are read-only here. Two CHECK constraints
 * ({@code CK_ADV_NOT_OVERDRAWN}, {@code CK_ADV_WAFAR_NOT_OVERSPENT}) make an
 * impossible state impossible to store, whichever route writes it.
 */
@Getter
@Setter
@Entity
@Table(name = "ATTACHE_ADVANCE_BALANCE", schema = "FINCORE")
public class AttacheAdvanceBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BALANCE_ID", nullable = false)
    private Integer id;

    /**
     * FIN-03: optimistic lock ({@code ROW_VERSION}, V148). Every counter on this row
     * was a read-modify-write with no lock, so two concurrent postings could each add
     * to the value they read and one of the two amounts was lost. The second now fails with a
     * version conflict (409) and changes nothing. Null until first persisted, so
     * Spring Data still treats a new row as new.
     */
    @Version
    @Column(name = "ROW_VERSION", nullable = false)
    private Long rowVersion;

    @Column(name = "FISCAL_YEAR_ID", nullable = false)
    private Integer fiscalYearId;

    /** The attaché, identified by the country it serves. */
    @Column(name = "STUDY_COUNTRY_IN_ID", nullable = false)
    private Integer studyCountryInId;

    @Column(name = "CURRENCY_ID", nullable = false)
    private Integer currencyId;

    /**
     * Cash held when the year opened. Entered once as a verified figure — not
     * derived from the 2024 archive, which has no attaché attribution
     * ({@code STUDY_FINANCE_DOF_ID} is 0 on all 941 rows).
     */
    @Column(name = "OPENING_BALANCE", nullable = false, precision = 18, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "TRANSFERRED", nullable = false, precision = 18, scale = 2)
    private BigDecimal transferred = BigDecimal.ZERO;

    /** All cash out to beneficiaries, whether funded by budget or by Wafar. */
    @Column(name = "DISBURSED", nullable = false, precision = 18, scale = 2)
    private BigDecimal disbursed = BigDecimal.ZERO;

    @Column(name = "RETURNED", nullable = false, precision = 18, scale = 2)
    private BigDecimal returned = BigDecimal.ZERO;

    @Column(name = "WAFAR_EARMARKED", nullable = false, precision = 18, scale = 2)
    private BigDecimal wafarEarmarked = BigDecimal.ZERO;

    @Column(name = "WAFAR_SPENT", nullable = false, precision = 18, scale = 2)
    private BigDecimal wafarSpent = BigDecimal.ZERO;

    /** Computed by the database: {@code wafarEarmarked - wafarSpent}. */
    @Column(name = "WAFAR_BALANCE", insertable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal wafarBalance;

    /** Computed by the database. Un-earmarked cash; never negative. */
    @Column(name = "AVAILABLE", insertable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal available;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;
}
