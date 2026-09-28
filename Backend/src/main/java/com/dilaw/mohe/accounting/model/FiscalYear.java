package com.dilaw.mohe.accounting.model;

import com.dilaw.mohe.codes.FiscalYearStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A fiscal year, {@code OPEN} then {@code CLOSED} then {@code LOCKED}.
 *
 * <p>Seeded as the calendar year. That matches the Gregorian years already in
 * the data and is the common case, but it is an assumption about Yemeni practice
 * rather than something the migration could verify — the dates are data, and
 * changing them is a one-row update.
 *
 * <p>Distinct from {@code MOHECORE.MOHE_QUARTER}, which is the scholarship
 * payment cycle. An allocation belongs to a payment quarter <em>and</em> is
 * posted in an accounting period; the two are related, not the same.
 */
@Getter
@Setter
@Entity
@Table(name = "FISCAL_YEAR", schema = "FINCORE")
public class FiscalYear {

    public static final String OPEN = FiscalYearStatus.OPEN;
    public static final String CLOSED = FiscalYearStatus.CLOSED;
    public static final String LOCKED = FiscalYearStatus.LOCKED;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FISCAL_YEAR_ID", nullable = false)
    private Integer id;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

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

    @Column(name = "CLOSED_BY")
    private Integer closedBy;

    @Column(name = "CLOSED_AT")
    private Instant closedAt;

    /** NULL = ministry-wide. A fiscal year is national, so this stays null. */
    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;

    @Column(name = "CREATED_BY")
    private Integer createdBy;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private Instant createdAt;
}
