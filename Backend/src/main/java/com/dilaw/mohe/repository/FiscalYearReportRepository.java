package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.FiscalYearReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Fiscal year report.
 *
 * <p>Disbursement totals, transaction counts, min/max/avg and the Q1–Q4 splits
 * were always real. Five further columns were literals and are now computed:
 *
 * <ul>
 *   <li>{@code scholarship_disbursements} used to be the <em>entire</em>
 *       {@code SUM(NET_AMOUNT)} and {@code regular_disbursements} a literal 0,
 *       so every dinar was classed as scholarship spending by construction.
 *       Both now split on {@code STUDENT_STUDY_TYPE.IS_SCHOLARSHIP} (V028).</li>
 *   <li>{@code scholarship_beneficiaries} / {@code regular_beneficiaries} were
 *       literal 0; both now count distinct students on the same flag.</li>
 *   <li>{@code bank_transfer_amount} / {@code cash_disbursement_amount} were
 *       literal 0; both now derive from whether the payment carries a bank or a
 *       cheque reference.</li>
 * </ul>
 *
 * <p><b>What is still zero, and why it is not a bug.</b>
 *
 * <ul>
 *   <li>{@code total_budget_allocated} and {@code budget_utilization_rate} are 0
 *       because {@code MOHE_QUARTER.BUDGET_AMOUNT} is NULL on all 12 quarter
 *       rows — no budget has been entered. {@code total_pending_amount} is
 *       consequently the negative of spending. Documented in V021.</li>
 *   <li>{@code cash_disbursement_amount} is 0 because every one of the 941
 *       finance rows carries a {@code STUDY_FINANCE_BANKE_ID}: all disbursements
 *       really are bank transfers. This one is a measurement, not a gap.</li>
 *   <li>The scholarship/regular figures will not sum to the totals —
 *       {@code STUDENT_STUDY_TYPE_ID} is "Not Specified" on 937 of 941 students,
 *       and unclassified students are counted in neither bucket rather than
 *       being silently folded into "regular".</li>
 * </ul>
 */
@Repository
public interface FiscalYearReportRepository extends JpaRepository<FiscalYearReport, FiscalYearReport.FiscalYearReportId> {

    @Query(value = """
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
                  AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
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
            ORDER BY yr.fiscal_year DESC
            """, nativeQuery = true)
    List<FiscalYearReport> findAllOrdered(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query(value = """
            SELECT TOP 1
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
                  AND YEAR(sf.STUDY_FINANCE_MIDATE) = :year
                  AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
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
            ORDER BY yr.study_country_id
            """, nativeQuery = true)
    FiscalYearReport findByYear(
            @Param("year") Integer year,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query(value = """
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
                  AND YEAR(sf.STUDY_FINANCE_MIDATE) >= :startYear
                  AND YEAR(sf.STUDY_FINANCE_MIDATE) <= :endYear
                  AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
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
            ORDER BY yr.fiscal_year
            """, nativeQuery = true)
    List<FiscalYearReport> findByYearRange(
            @Param("startYear") Integer startYear,
            @Param("endYear") Integer endYear,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query(value = """
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
                  AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
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
            WHERE CASE WHEN ISNULL(budget.total_budget_allocated, 0) = 0 THEN CAST(0 AS DECIMAL(5,2))
                       ELSE CAST(yr.total_amount_disbursed * 100.0 / budget.total_budget_allocated AS DECIMAL(5,2)) END >= :minRate
            ORDER BY budget_utilization_rate DESC
            """, nativeQuery = true)
    List<FiscalYearReport> findByMinimumUtilizationRate(
            @Param("minRate") java.math.BigDecimal minRate,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);
}
