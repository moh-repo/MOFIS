package com.dilaw.mohe.accounting.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An entitlement that was not disbursed, and so became Wafar (وفر).
 *
 * <p><b>This moves no money.</b> Wafar is not new funds — it is budget already
 * transferred to an attaché that was not paid out. Booking it as income would
 * double-count the budget; booking it as an expense would double-count the
 * spend. An earmark records only that part of an advance the ministry already
 * holds is now spoken for.
 *
 * <p>The existing schema has always agreed: {@code STUDENT_FINANCE.NET_AMOUNT}
 * is a persisted computed column, {@code received - wafar}, so Wafar has been
 * the undisbursed part of an entitlement all along — there was simply nowhere to
 * record why, or which transfer it came out of.
 *
 * <p>{@link #wafarTypeId} points at {@code MOHECORE.STUDY_WAFAR_TYPE}, the
 * taxonomy that already exists: وفر, زيادة, إرجاع مبالغ زائدة, تسوية بدل السكن,
 * تسوية مبلغ المنحة, خصم تذكرة سفر, خصم رسوم دراسية, أخرى.
 */
@Getter
@Setter
@Entity
@Table(name = "WAFAR_EARMARK", schema = "FINCORE")
public class WafarEarmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EARMARK_ID", nullable = false)
    private Integer id;

    @Column(name = "FISCAL_YEAR_ID", nullable = false)
    private Integer fiscalYearId;

    @Column(name = "MOHE_QUARTER_KEY", nullable = false)
    private Integer moheQuarterKey;

    /** The attaché holding the savings. Wafar never leaves it. */
    @Column(name = "STUDY_COUNTRY_IN_ID", nullable = false)
    private Integer studyCountryInId;

    /** Whose entitlement this was. Null for an earmark not tied to one student. */
    @Column(name = "STUDENT_ID")
    private Integer studentId;

    @Column(name = "FEE_TYPE_ID", nullable = false)
    private Integer feeTypeId;

    /** The reason, from {@code MOHECORE.STUDY_WAFAR_TYPE}. */
    @Column(name = "WAFAR_TYPE_ID")
    private Integer wafarTypeId;

    @Column(name = "CURRENCY_ID", nullable = false)
    private Integer currencyId;

    @Column(name = "AMOUNT", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** True when only part of the entitlement went to Wafar. */
    @Column(name = "IS_PARTIAL", nullable = false)
    private Boolean partial = false;

    /** The transfer this money came out of, where it is known. */
    @Column(name = "SOURCE_ALLOCATION_ID")
    private Integer sourceAllocationId;

    @Column(name = "BATCH_ID")
    private Integer batchId;

    @Nationalized
    @Column(name = "NOTES", length = 500)
    private String notes;

    @Column(name = "CREATED_BY")
    private Integer createdBy;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private Instant createdAt;
}
