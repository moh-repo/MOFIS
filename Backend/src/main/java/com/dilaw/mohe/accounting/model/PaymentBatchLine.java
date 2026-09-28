package com.dilaw.mohe.accounting.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One student's line in a payment batch: what they were due, and what they got.
 *
 * <p>{@link #heldReason} is the point of the table. When a student is in a batch
 * but not paid in full, the reason is recorded against that batch rather than
 * inferred later from a status code — which is what makes "who fell out of batch
 * 1, and why" answerable at all.
 *
 * <p>A line can never disburse more than it entitles; the shortfall is what
 * becomes a {@link WafarEarmark} at quarter close.
 */
@Getter
@Setter
@Entity
@Table(name = "PAYMENT_BATCH_LINE", schema = "FINCORE")
public class PaymentBatchLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BATCH_LINE_ID", nullable = false)
    private Integer id;

    @Column(name = "BATCH_ID", nullable = false)
    private Integer batchId;

    @Column(name = "STUDENT_ID", nullable = false)
    private Integer studentId;

    @Column(name = "ENTITLED_AMOUNT", nullable = false, precision = 18, scale = 2)
    private BigDecimal entitledAmount;

    @Column(name = "DISBURSED_AMOUNT", nullable = false, precision = 18, scale = 2)
    private BigDecimal disbursedAmount = BigDecimal.ZERO;

    /** Why the student was not paid in full, when that is the case. */
    @Nationalized
    @Column(name = "HELD_REASON", length = 300)
    private String heldReason;

    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private Instant createdAt;
}
