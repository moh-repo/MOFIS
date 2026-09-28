package com.dilaw.mohe.accounting.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One account in the chart (الدليل المحاسبي).
 *
 * <p>Bean constraints (plan item 2.6) are width and vocabulary only. The
 * required fields are deliberately left to {@code CoaAccountService}: the same
 * body serves create and update, update is partial (a null means "unchanged"),
 * and on create the service fills {@code levelNo}, {@code segmentId},
 * {@code fullCode} and {@code normalBalance} from the parent — a {@code @NotNull}
 * on any of those would refuse every child account.
 *
 * <p>The hierarchy is fixed by the Executive Regulation of the Financial Law —
 * أبواب، بنود، أنواع، مجموعات، حسابات — but the Regulation also defines what
 * those levels are called and what codes they carry, so neither the level names
 * nor any code value is hard-coded here. Levels live in {@link CoaLevel}, and
 * V041 ships the table with no accounts at all.
 *
 * <p>Most of the invariants are enforced by the database rather than here
 * ({@code TR_COA_ACCOUNT_VALIDATE}), because a chart of accounts is edited by
 * more than one route — the maintenance screen, the manual import, and whatever
 * SSMS session an administrator opens. The two that matter most:
 * <b>only a leaf may be posted to</b>, since a parent that receives postings
 * makes its own subtotal disagree with the sum of its children; and
 * <b>{@code fullCode} must equal the parent's {@code fullCode} plus this
 * account's {@code code}</b>.
 *
 * <p>Accounts are deactivated, never deleted: one that has been posted to has to
 * survive for the audit trail even after it stops being used.
 */
@Getter
@Setter
@Entity
@Table(name = "COA_ACCOUNT", schema = "FINCORE")
public class CoaAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ACCOUNT_ID", nullable = false)
    private Integer id;

    @Column(name = "SEGMENT_ID", nullable = false)
    private Integer segmentId;

    /** 1 = باب, 2 = بند, and so on. Meaning comes from {@link CoaLevel}. */
    @Column(name = "LEVEL_NO", nullable = false)
    private Short levelNo;

    @Column(name = "PARENT_ACCOUNT_ID")
    private Integer parentAccountId;

    /** The code at this level only. */
    @Nationalized
    @Size(max = 20)
    @Column(name = "CODE", nullable = false, length = 20)
    private String code;

    /**
     * The materialised path: the parent's {@code fullCode} followed by
     * {@link #code}. Stored rather than derived so it can be indexed and
     * reported on; the database checks it against the parent on every write.
     */
    @Nationalized
    @Size(max = 80)
    @Column(name = "FULL_CODE", nullable = false, length = 80)
    private String fullCode;

    @Nationalized
    @Size(max = 300)
    @Column(name = "NAME_AR", nullable = false, length = 300)
    private String nameAr;

    @Nationalized
    @Size(max = 300)
    @Column(name = "NAME_EN", length = 300)
    private String nameEn;

    /** ASSET, LIABILITY, EQUITY, REVENUE or EXPENSE. */
    @Pattern(regexp = "ASSET|LIABILITY|EQUITY|REVENUE|EXPENSE",
            message = "accountNature must be one of ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE")
    @Column(name = "ACCOUNT_NATURE", nullable = false, length = 12)
    private String accountNature;

    /** 'D' or 'C'. Must agree with {@link #accountNature} — a CHECK enforces it. */
    @Pattern(regexp = "[DC]")
    @Column(name = "NORMAL_BALANCE", nullable = false, length = 1)
    @Size(max = 1)
    private String normalBalance;

    /** Only a leaf may carry postings. */
    @Column(name = "IS_POSTABLE", nullable = false)
    private Boolean postable = false;

    @Column(name = "IS_BUDGETARY", nullable = false)
    private Boolean budgetary = false;

    /** The chart is versioned per year: renumbering for 2027 must not rewrite 2026. */
    @Column(name = "FISCAL_YEAR_ID", nullable = false)
    private Integer fiscalYearId;

    @Column(name = "EFFECTIVE_FROM")
    private LocalDate effectiveFrom;

    @Column(name = "EFFECTIVE_TO")
    private LocalDate effectiveTo;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean active = true;

    /** NULL means ministry-wide, which is the normal case for a national chart. */
    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;

    @Column(name = "CREATED_BY")
    private Integer createdBy;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_BY")
    private Integer updatedBy;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;
}
