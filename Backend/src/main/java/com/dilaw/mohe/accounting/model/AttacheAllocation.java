package com.dilaw.mohe.accounting.model;

import com.dilaw.mohe.codes.AllocationStatus;
import com.dilaw.mohe.codes.AllocationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A quarterly transfer of budget to one cultural attaché.
 *
 * <p>{@link #allocationType} separates the regular quarterly transfer from an
 * <b>Additional Budget Allocation</b>. Additional allocations are first-class
 * rows rather than adjustments to the original, because they are applied
 * quarterly and retroactively — covering students added, edited or corrected
 * after the regular transfer went out.
 *
 * <p>Amounts are always positive. A downward correction does not become a
 * negative allocation: the cash is already sitting at the attaché, so it becomes
 * a {@link WafarEarmark} instead.
 */
@Getter
@Setter
@Entity
@Table(name = "ATTACHE_ALLOCATION", schema = "FINCORE")
public class AttacheAllocation {

    public static final String REGULAR = AllocationType.REGULAR;
    public static final String ADDITIONAL = AllocationType.ADDITIONAL;

    public static final String DRAFT = AllocationStatus.DRAFT;
    public static final String APPROVED = AllocationStatus.APPROVED;
    public static final String TRANSFERRED = AllocationStatus.TRANSFERRED;
    public static final String CANCELLED = AllocationStatus.CANCELLED;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ALLOCATION_ID", nullable = false)
    private Integer id;

    /**
     * FIN-03: optimistic lock ({@code ROW_VERSION}, V148). Every status change on this
     * row was a read-modify-write with no lock, so two concurrent requests could both
     * pass the status check and both move the money (an allocation transferred twice grew the
     * advance twice). The second now fails with a
     * version conflict (409) and changes nothing. Null until first persisted, so
     * Spring Data still treats a new row as new.
     */
    @Version
    @Column(name = "ROW_VERSION", nullable = false)
    private Long rowVersion;

    @Column(name = "FISCAL_YEAR_ID", nullable = false)
    private Integer fiscalYearId;

    /**
     * The {@code YYYYQ} key of {@code MOHECORE.MOHE_QUARTER} — e.g. 20242 for
     * 2024 quarter 2. The same value {@code STUDENT_FINANCE} already carries in
     * {@code STUDENT_FIN_RECIEVED_YEAR_MONTH}.
     */
    @Column(name = "MOHE_QUARTER_KEY", nullable = false)
    private Integer moheQuarterKey;

    /** The attaché receiving the transfer, identified by the country it serves. */
    @Column(name = "STUDY_COUNTRY_IN_ID", nullable = false)
    private Integer studyCountryInId;

    @Column(name = "ALLOCATION_TYPE", nullable = false, length = 12)
    private String allocationType = REGULAR;

    /** Null when the allocation covers every stream rather than one. */
    @Column(name = "FEE_TYPE_ID")
    private Integer feeTypeId;

    @Column(name = "CURRENCY_ID", nullable = false)
    private Integer currencyId;

    @Column(name = "AMOUNT", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "STATUS", nullable = false, length = 15)
    private String status = DRAFT;

    @Column(name = "TRANSFER_DATE")
    private LocalDate transferDate;

    @Nationalized
    @Column(name = "TRANSFER_REFERENCE", length = 100)
    private String transferReference;

    /** {@code ALERTCORE.ApprovalRequests.Id} — issuing an allocation is an approved act. */
    @Column(name = "APPROVAL_REQUEST_ID")
    private Integer approvalRequestId;

    @Nationalized
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
