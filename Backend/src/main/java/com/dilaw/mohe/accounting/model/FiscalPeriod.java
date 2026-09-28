package com.dilaw.mohe.accounting.model;

import com.dilaw.mohe.codes.FiscalPeriodStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One accounting period: twelve months, plus period 13.
 *
 * <p>Period 13 carries no calendar days of its own — it shares 31 December — and
 * exists for قيود التسوية, the year-end adjusting entries. Without it, an
 * adjustment agreed in March against last year has nowhere to go except
 * December, where it silently changes a month that was already reported.
 *
 * <p>Closing is one-way. Reopening is possible but deliberately awkward: the
 * caller must set {@code SESSION_CONTEXT('fincore_allow_reopen')} first, so that
 * reopening a closed period is an explicit act rather than an UPDATE that
 * happens to succeed. See {@code TR_FISCAL_PERIOD_CLOSE_RULES} in V040.
 */
@Getter
@Setter
@Entity
@Table(name = "FISCAL_PERIOD", schema = "FINCORE")
public class FiscalPeriod {

    public static final String OPEN = FiscalPeriodStatus.OPEN;
    public static final String CLOSED = FiscalPeriodStatus.CLOSED;

    /** The adjustment period, for entries made against a year after it ends. */
    public static final short ADJUSTMENT_PERIOD_NO = 13;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FISCAL_PERIOD_ID", nullable = false)
    private Integer id;

    @Column(name = "FISCAL_YEAR_ID", nullable = false)
    private Integer fiscalYearId;

    @Column(name = "PERIOD_NO", nullable = false)
    private Short periodNo;

    @Nationalized
    @Column(name = "NAME_AR", length = 100)
    private String nameAr;

    @Nationalized
    @Column(name = "NAME_EN", length = 100)
    private String nameEn;

    @Column(name = "START_DATE", nullable = false)
    private LocalDate startDate;

    @Column(name = "END_DATE", nullable = false)
    private LocalDate endDate;

    @Column(name = "STATUS", nullable = false, length = 10)
    private String status = OPEN;

    @Column(name = "IS_ADJUSTMENT", nullable = false)
    private Boolean adjustment = false;

    @Column(name = "CLOSED_BY")
    private Integer closedBy;

    @Column(name = "CLOSED_AT")
    private Instant closedAt;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private Instant createdAt;
}
