package com.dilaw.mohe.accounting.model;

import com.dilaw.mohe.codes.PaymentBatchStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

/**
 * A payment batch — the دفعة, which the legacy schema calls DOF.
 *
 * <p>Batch 1 pays most students; those missing documents or attache requirements
 * are paid in batch 2, and so on within the quarter. That makes the batch the
 * document the whole cycle hangs on: it is prepared, approved, then disbursed.
 *
 * <p>It replaces {@code STUDENT_FINANCE.STUDY_FINANCE_DOF_ID} for new data
 * without touching the old column, for two reasons. That column is 0 on all 941
 * rows, so the batch is not actually recorded anywhere today; and its lookup,
 * {@code STUDY_FINANCE_DOF}, holds two different dimensions at once — ids 1-4
 * are the batch numbers, while ids 100-107 are cultural attache offices.
 */
@Getter
@Setter
@Entity
@Table(name = "PAYMENT_BATCH", schema = "FINCORE")
public class PaymentBatch {

    public static final String OPEN = PaymentBatchStatus.OPEN;
    public static final String APPROVED = PaymentBatchStatus.APPROVED;
    public static final String DISBURSED = PaymentBatchStatus.DISBURSED;
    public static final String CLOSED = PaymentBatchStatus.CLOSED;
    public static final String CANCELLED = PaymentBatchStatus.CANCELLED;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BATCH_ID", nullable = false)
    private Integer id;

    @Column(name = "FISCAL_YEAR_ID", nullable = false)
    private Integer fiscalYearId;

    /** The {@code YYYYQ} key of {@code MOHECORE.MOHE_QUARTER}, e.g. 20261. */
    @Column(name = "MOHE_QUARTER_KEY", nullable = false)
    private Integer moheQuarterKey;

    /** The attache paying, identified by the country it serves. */
    @Column(name = "STUDY_COUNTRY_IN_ID", nullable = false)
    private Integer studyCountryInId;

    /** The DOF sequence within the quarter: 1, 2, 3... */
    @Column(name = "BATCH_NO", nullable = false)
    private Short batchNo;

    @Column(name = "FEE_TYPE_ID")
    private Integer feeTypeId;

    @Column(name = "CURRENCY_ID", nullable = false)
    private Integer currencyId;

    @Column(name = "STATUS", nullable = false, length = 15)
    private String status = OPEN;

    @Column(name = "PREPARED_BY")
    private Integer preparedBy;

    @Column(name = "PREPARED_AT")
    private Instant preparedAt;

    @Column(name = "APPROVED_BY")
    private Integer approvedBy;

    @Column(name = "APPROVED_AT")
    private Instant approvedAt;

    @Column(name = "DISBURSED_AT")
    private Instant disbursedAt;

    @Column(name = "APPROVAL_REQUEST_ID")
    private Integer approvalRequestId;

    @Nationalized
    @Column(name = "NOTES", length = 500)
    private String notes;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private Instant createdAt;
}
