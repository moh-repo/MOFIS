package com.dilaw.mohe.accounting.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Wafar spent, with permission, on another student or an expense.
 *
 * <p>Unlike a {@link WafarEarmark}, this <em>does</em> move money: cash leaves
 * the attaché. It releases the earmark and records an outflow at the same time,
 * so the attaché's un-earmarked cash is unchanged.
 *
 * <p><b>Wafar stays where it is.</b> Cairo's savings pay only Cairo's students —
 * {@code TR_WAFAR_USE_SAME_ATTACHE} refuses a beneficiary who studies in another
 * country, and refuses a use larger than the savings held. The rule lives in the
 * database because it is the one most easily broken by a well-meaning screen
 * that lets an officer pick any student.
 */
@Getter
@Setter
@Entity
@Table(name = "WAFAR_USE", schema = "FINCORE")
public class WafarUse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USE_ID", nullable = false)
    private Integer id;

    @Column(name = "FISCAL_YEAR_ID", nullable = false)
    private Integer fiscalYearId;

    @Column(name = "MOHE_QUARTER_KEY")
    private Integer moheQuarterKey;

    /** The attaché whose pool is drawn on. */
    @Column(name = "STUDY_COUNTRY_IN_ID", nullable = false)
    private Integer studyCountryInId;

    @Column(name = "CURRENCY_ID", nullable = false)
    private Integer currencyId;

    @Column(name = "AMOUNT", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** Null for a general expense rather than a payment to a student. */
    @Column(name = "BENEFICIARY_STUDENT_ID")
    private Integer beneficiaryStudentId;

    /** {@code MOHECORE.WAFAR_SPENDING_CATEGORY}: university fee, individual payment, operating expense, salary, travel, other. */
    @Column(name = "CATEGORY_ID")
    private Integer categoryId;

    /** The pre-existing {@code WAFAR_SPENDING} row this corresponds to, where there is one. */
    @Column(name = "SPENDING_ID")
    private Integer spendingId;

    /** Reusing Wafar needs permission — {@code ALERTCORE.ApprovalRequests.Id}. */
    @Column(name = "APPROVAL_REQUEST_ID")
    private Integer approvalRequestId;

    @Nationalized
    @Column(name = "NOTES", length = 500)
    private String notes;

    @Column(name = "CREATED_BY")
    private Integer createdBy;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private Instant createdAt;
}
