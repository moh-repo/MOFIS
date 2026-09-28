package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * One row per (fiscal year, study country) for the fiscal year report.
 *
 * <p>Mapped as a subselect rather than the {@code V_Fiscal_Year_Report} view it
 * used to name: the view's columns no longer matched the entity, so anything but
 * the repository's native queries failed with "Invalid column name". The subselect
 * is the native SELECT without its filter parameters; V066 drops the view. See
 * {@code FiscalYearReportRepository} for which columns are measured and which are
 * zero because nothing is recorded.
 */
@Getter
@Setter
@Entity
@Immutable
@Subselect("""
    SELECT
        yr.fiscal_year,
        yr.study_country_id,
        CAST(ISNULL(budget.total_budget_allocated, 0) AS DECIMAL(18,2)) AS total_budget_allocated,
        yr.total_amount_disbursed,
        CAST(ISNULL(budget.total_budget_allocated, 0) - yr.total_amount_disbursed AS DECIMAL(18,2)) AS total_pending_amount,
        CASE WHEN ISNULL(budget.total_budget_allocated, 0) = 0 THEN CAST(0 AS DECIMAL(5,2))
             ELSE CAST(yr.total_amount_disbursed * 100.0 / budget.total_budget_allocated AS DECIMAL(5,2)) END AS budget_utilization_rate,
        yr.total_transactions,
        yr.avg_transaction_amount,
        yr.max_transaction_amount,
        yr.min_transaction_amount,
        yr.scholarship_disbursements,
        yr.regular_disbursements,
        yr.bank_transfer_amount,
        yr.cash_disbursement_amount,
        yr.total_beneficiaries,
        yr.scholarship_beneficiaries,
        yr.regular_beneficiaries,
        yr.q1_disbursements,
        yr.q2_disbursements,
        yr.q3_disbursements,
        yr.q4_disbursements
    FROM (
        SELECT
            YEAR(sf.STUDY_FINANCE_MIDATE) AS fiscal_year,
            sd.STUDY_COUNTRY_IN AS study_country_id,
            CAST(SUM(sf.NET_AMOUNT) AS DECIMAL(18,2)) AS total_amount_disbursed,
            COUNT(*) AS total_transactions,
            CASE WHEN COUNT(*) = 0 THEN CAST(0 AS DECIMAL(18,2))
                 ELSE CAST(SUM(sf.NET_AMOUNT) * 1.0 / COUNT(*) AS DECIMAL(18,2)) END AS avg_transaction_amount,
            CAST(MAX(sf.NET_AMOUNT) AS DECIMAL(18,2)) AS max_transaction_amount,
            CAST(MIN(sf.NET_AMOUNT) AS DECIMAL(18,2)) AS min_transaction_amount,
            CAST(SUM(CASE WHEN sty.IS_SCHOLARSHIP = 1 THEN sf.NET_AMOUNT ELSE 0 END) AS DECIMAL(18,2)) AS scholarship_disbursements,
            CAST(SUM(CASE WHEN sty.IS_SCHOLARSHIP = 0 THEN sf.NET_AMOUNT ELSE 0 END) AS DECIMAL(18,2)) AS regular_disbursements,
            CAST(SUM(CASE WHEN sf.STUDY_FINANCE_BANKE_ID IS NOT NULL THEN sf.NET_AMOUNT ELSE 0 END) AS DECIMAL(18,2)) AS bank_transfer_amount,
            CAST(SUM(CASE WHEN sf.STUDY_FINANCE_BANKE_ID IS NULL AND sf.STUDY_CHECK_ID IS NULL THEN sf.NET_AMOUNT ELSE 0 END) AS DECIMAL(18,2)) AS cash_disbursement_amount,
            COUNT(DISTINCT sd.STUDENT_ID) AS total_beneficiaries,
            COUNT(DISTINCT CASE WHEN sty.IS_SCHOLARSHIP = 1 THEN sd.STUDENT_ID END) AS scholarship_beneficiaries,
            COUNT(DISTINCT CASE WHEN sty.IS_SCHOLARSHIP = 0 THEN sd.STUDENT_ID END) AS regular_beneficiaries,
            CAST(SUM(CASE WHEN DATEPART(QUARTER, sf.STUDY_FINANCE_MIDATE) = 1 THEN sf.NET_AMOUNT ELSE 0 END) AS DECIMAL(18,2)) AS q1_disbursements,
            CAST(SUM(CASE WHEN DATEPART(QUARTER, sf.STUDY_FINANCE_MIDATE) = 2 THEN sf.NET_AMOUNT ELSE 0 END) AS DECIMAL(18,2)) AS q2_disbursements,
            CAST(SUM(CASE WHEN DATEPART(QUARTER, sf.STUDY_FINANCE_MIDATE) = 3 THEN sf.NET_AMOUNT ELSE 0 END) AS DECIMAL(18,2)) AS q3_disbursements,
            CAST(SUM(CASE WHEN DATEPART(QUARTER, sf.STUDY_FINANCE_MIDATE) = 4 THEN sf.NET_AMOUNT ELSE 0 END) AS DECIMAL(18,2)) AS q4_disbursements
        FROM MOHECORE.STUDENT_FINANCE sf
        INNER JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = sf.STUDENT_ID AND ISNULL(sd.IS_DELETED, 0) = 0
        LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE sty ON sty.STUDENT_STUDY_TYPE_ID = sd.STUDENT_STUDY_TYPE_ID
        WHERE sf.STUDY_FINANCE_MIDATE IS NOT NULL
        GROUP BY sd.STUDY_COUNTRY_IN, YEAR(sf.STUDY_FINANCE_MIDATE)
    ) yr
    LEFT JOIN (
        SELECT sd.STUDY_COUNTRY_IN AS study_country_id, mq.QUARTER_YEAR AS fiscal_year,
               CAST(SUM(ISNULL(mq.BUDGET_AMOUNT, 0)) AS DECIMAL(18,2)) AS total_budget_allocated
        FROM MOHECORE.MOHE_QUARTER mq
        LEFT JOIN MOHECORE.STUDENT_FINANCE sf ON sf.MOHE_QUARTER_ID = mq.QUARTER
        LEFT JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = sf.STUDENT_ID AND ISNULL(sd.IS_DELETED, 0) = 0
        GROUP BY sd.STUDY_COUNTRY_IN, mq.QUARTER_YEAR
    ) budget ON budget.study_country_id = yr.study_country_id AND budget.fiscal_year = yr.fiscal_year
""")
@Synchronize({"STUDENT_FINANCE", "STUDENT_DATA", "STUDENT_STUDY_TYPE", "MOHE_QUARTER"})
@IdClass(FiscalYearReport.FiscalYearReportId.class)
public class FiscalYearReport {

    @Id
    @Column(name = "fiscal_year")
    private Integer fiscalYear;

    @Id
    @Column(name = "study_country_id")
    private Integer countryId;

    @Column(name = "total_budget_allocated")
    private BigDecimal totalBudgetAllocated;

    @Column(name = "total_amount_disbursed")
    private BigDecimal totalAmountDisbursed;

    @Column(name = "total_pending_amount")
    private BigDecimal totalPendingAmount;

    @Column(name = "budget_utilization_rate")
    private BigDecimal budgetUtilizationRate;

    @Column(name = "total_transactions")
    private Long totalTransactions;

    @Column(name = "avg_transaction_amount")
    private BigDecimal avgTransactionAmount;

    @Column(name = "max_transaction_amount")
    private BigDecimal maxTransactionAmount;

    @Column(name = "min_transaction_amount")
    private BigDecimal minTransactionAmount;

    @Column(name = "scholarship_disbursements")
    private BigDecimal scholarshipDisbursements;

    @Column(name = "regular_disbursements")
    private BigDecimal regularDisbursements;

    @Column(name = "bank_transfer_amount")
    private BigDecimal bankTransferAmount;

    @Column(name = "cash_disbursement_amount")
    private BigDecimal cashDisbursementAmount;

    @Column(name = "total_beneficiaries")
    private Integer totalBeneficiaries;

    @Column(name = "scholarship_beneficiaries")
    private Integer scholarshipBeneficiaries;

    @Column(name = "regular_beneficiaries")
    private Integer regularBeneficiaries;

    @Column(name = "q1_disbursements")
    private BigDecimal q1Disbursements;

    @Column(name = "q2_disbursements")
    private BigDecimal q2Disbursements;

    @Column(name = "q3_disbursements")
    private BigDecimal q3Disbursements;

    @Column(name = "q4_disbursements")
    private BigDecimal q4Disbursements;

    @Getter
    @Setter
    @lombok.EqualsAndHashCode
    public static class FiscalYearReportId implements Serializable {
        private Integer fiscalYear;
        private Integer countryId;
    }
}
