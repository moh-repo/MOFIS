package com.dilaw.mohe.accounting.model;

import com.dilaw.mohe.codes.AppropriationStatus;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The annual budget the ministry receives from MOF, by stream.
 *
 * <p>The head of the chain: this is what the quarterly {@link AttacheAllocation}
 * transfers draw down. {@link #referenceNumber} carries the MOF decision the
 * appropriation came from, so a figure can be traced back to the document that
 * authorised it.
 *
 * <p>Bean constraints (plan item 2.6): the amount is required and positive on
 * both create and update; the three foreign keys are required by the table but
 * only read on create (update takes amount, reference and notes), so they stay
 * with {@code BudgetAppropriationService.validate} rather than refusing every
 * update body that omits them.
 */
@Getter
@Setter
@Entity
@Table(name = "BUDGET_APPROPRIATION", schema = "FINCORE")
public class BudgetAppropriation {

    public static final String DRAFT = AppropriationStatus.DRAFT;
    public static final String APPROVED = AppropriationStatus.APPROVED;
    public static final String REVISED = AppropriationStatus.REVISED;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "APPROPRIATION_ID", nullable = false)
    private Integer id;

    @Column(name = "FISCAL_YEAR_ID", nullable = false)
    private Integer fiscalYearId;

    @Column(name = "FEE_TYPE_ID", nullable = false)
    private Integer feeTypeId;

    @Column(name = "CURRENCY_ID", nullable = false)
    private Integer currencyId;

    @NotNull(message = "amount is required")
    @Positive(message = "An appropriation must be a positive amount.")
    @Column(name = "AMOUNT", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** NULL = the whole ministry, not broken down by attache. */
    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;

    @Size(max = 15)
    @Column(name = "STATUS", nullable = false, length = 15)
    private String status = DRAFT;

    /** The MOF decision this appropriation came from. */
    @Nationalized
    @Size(max = 100)
    @Column(name = "REFERENCE_NUMBER", length = 100)
    private String referenceNumber;

    @Nationalized
    @Size(max = 500)
    @Column(name = "NOTES", length = 500)
    private String notes;

    @Column(name = "CREATED_BY")
    private Integer createdBy;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_BY")
    private Integer updatedBy;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;
}
