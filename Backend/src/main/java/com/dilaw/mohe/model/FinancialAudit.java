package com.dilaw.mohe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Immutable
/**
 * Finance records presented as an audit listing.
 *
 * <p>Every column here is read from the database or left empty. It used to
 * substitute literals wherever a lookup did not resolve — {@code 'Finance
 * Department'} for the auditor, {@code 'Reviewed automatically'} for the notes,
 * {@code 'USD'} for the currency — which on an audit report asserted an
 * attribution, a review and a currency that no record supports. In this database
 * none of those lookups resolve (STUDY_FINANCE_DOF_ID is 0 on all 941 rows and
 * the received-status is 0), so the literals were not edge cases: they were what
 * nearly every row displayed.
 *
 * <p>This is not an audit trail. Real change history lives in
 * {@code AUDITLOG.AuditTrail}; this view reports finance transactions.
 */
@Subselect("""
    SELECT
        CAST(ROW_NUMBER() OVER (
            ORDER BY COALESCE(sf.STUDY_CHECK_DATE, sf.STUDY_FINANCE_MIDATE) DESC, sf.STUDENT_FIN_ID
        ) AS BIGINT) AS audit_id,
        CAST(COALESCE(sf.STUDY_CHECK_DATE, sf.STUDY_FINANCE_MIDATE) AS DATE) AS audit_date,
        CAST(NULLIF(srws.STUDY_RECEIVED_WHOM_STATUS_NAME, '') AS NVARCHAR(255)) AS audit_category,
        CAST(NULLIF(COALESCE(NULLIF(srws.STUDY_RECEIVED_WHOM_STATUS_NAME_EN, ''), srws.STUDY_RECEIVED_WHOM_STATUS_NAME), '') AS NVARCHAR(255)) AS audit_category_en,
        CAST(COALESCE(sf.STUDENT_FIN_RECIEVED_AMOUNT, 0) AS DECIMAL(18, 2)) AS audit_amount,
        CAST(NULLIF(cur.STUDY_CURRENCY_IN_NAME, '') AS NVARCHAR(100)) AS audit_currency,
        CAST(NULLIF(COALESCE(NULLIF(cur.STUDY_CURRENCY_IN_NAME_EN, ''), cur.STUDY_CURRENCY_IN_NAME), '') AS NVARCHAR(100)) AS audit_currency_en,
        CAST(NULLIF(sfrs.STUDY_FIN_RECEIVED_STATUS_NAME, '') AS NVARCHAR(255)) AS audit_status,
        CAST(NULLIF(COALESCE(NULLIF(sfrs.STUDY_FIN_RECEIVED_STATUS_NAME_EN, ''), sfrs.STUDY_FIN_RECEIVED_STATUS_NAME), '') AS NVARCHAR(255)) AS audit_status_en,
        CAST(NULLIF(dof.STUDY_FINANCE_DOF_NAME, '') AS NVARCHAR(255)) AS auditor_name,
        CAST(NULLIF(COALESCE(NULLIF(dof.STUDY_FINANCE_DOF_NAME_EN, ''), dof.STUDY_FINANCE_DOF_NAME), '') AS NVARCHAR(255)) AS auditor_name_en,
        CAST(COALESCE(
                NULLIF(CAST(sf.STUDY_CHECK_DETAILS AS NVARCHAR(MAX)), ''),
                NULLIF(CAST(sf.STUDY_NOTE_1 AS NVARCHAR(MAX)), '')
            ) AS NVARCHAR(1000)) AS audit_notes,
        sf.STUDENT_ID AS student_id,
        CAST(sd.STUDENT_NAME AS NVARCHAR(255)) AS student_name,
        CAST(sd.STUDENT_PASSPORT_NUMBER AS NVARCHAR(255)) AS student_passport,
        CAST(bank.STUDY_FINANCE_BANKE_NAME AS NVARCHAR(255)) AS bank_name,
        CAST(COALESCE(bank.STUDY_FINANCE_BANKE_NAME_EN, bank.STUDY_FINANCE_BANKE_NAME) AS NVARCHAR(255)) AS bank_name_en,
        sf.STUDENT_FIN_RECIEVED_YEAR AS fiscal_year,
        CASE
            -- The column holds the quarter (1-4), not a month (R5-31).
            WHEN sf.STUDENT_FIN_RECIEVED_MONTH BETWEEN 1 AND 4 THEN sf.STUDENT_FIN_RECIEVED_MONTH
            ELSE NULL
        END AS fiscal_quarter
    FROM MOHECORE.STUDENT_FINANCE sf
    LEFT JOIN MOHECORE.STUDY_FIN_RECEIVED_STATUS sfrs
        ON sf.STUDY_FIN_RECIEVED_STATUS = sfrs.STUDY_FIN_RECEIVED_STATUS_ID
    LEFT JOIN MOHECORE.STUDY_RECEIVED_WHOM_STATUS srws
        ON sf.STUDY_RECEIVED_WHOM_STATUS_ID = srws.STUDY_RECEIVED_WHOM_STATUS_ID
    LEFT JOIN MOHECORE.STUDY_FINANCE_DOF dof
        ON sf.STUDY_FINANCE_DOF_ID = dof.STUDY_FINANCE_DOF_ID
    LEFT JOIN MOHECORE.STUDY_FINANCE_BANK bank
        ON sf.STUDY_FINANCE_BANKE_ID = bank.STUDY_FINANCE_BANKE_ID
    LEFT JOIN MOHECORE.STUDY_CURRENCY_IN cur
        ON sf.STUDY_CURRENCY_IN_ID = cur.STUDY_CURRENCY_IN_ID
    LEFT JOIN MOHECORE.STUDENT_DATA sd
        ON sf.STUDENT_ID = sd.STUDENT_ID
    WHERE sf.STUDENT_FIN_RECIEVED_AMOUNT IS NOT NULL
      AND ISNULL(sd.IS_DELETED, 0) = 0
""")
@Synchronize({
        "STUDENT_FINANCE",
        "STUDY_FIN_RECEIVED_STATUS",
        "STUDY_RECEIVED_WHOM_STATUS",
        "STUDY_FINANCE_DOF",
        "STUDY_FINANCE_BANK",
        "STUDY_CURRENCY_IN",
        "STUDENT_DATA"
})
public class FinancialAudit {

    @Id
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "audit_date")
    private LocalDate auditDate;

    @Column(name = "audit_category")
    private String auditCategory;

    /** The same name in English; resolved by BilingualRows for an EN render (QA B6). */
    @Column(name = "audit_category_en")
    private String auditCategoryEn;

    @Column(name = "audit_amount")
    private BigDecimal auditAmount;

    @Column(name = "audit_currency")
    private String auditCurrency;

    /** The same name in English; resolved by BilingualRows for an EN render (QA B6). */
    @Column(name = "audit_currency_en")
    private String auditCurrencyEn;

    @Column(name = "audit_status")
    private String auditStatus;

    /** The same name in English; resolved by BilingualRows for an EN render (QA B6). */
    @Column(name = "audit_status_en")
    private String auditStatusEn;

    /** A STUDY_FINANCE_DOF lookup name, not a person's, so it has an English sibling. */
    @Column(name = "auditor_name")
    private String auditorName;

    /** The same name in English; resolved by BilingualRows for an EN render (QA B6). */
    @Column(name = "auditor_name_en")
    private String auditorNameEn;

    @Column(name = "audit_notes")
    private String auditNotes;

    @Column(name = "student_id")
    private Integer studentId;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "student_passport")
    private String studentPassport;

    @Column(name = "bank_name")
    private String bankName;

    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "bank_name_en")
    private String bankNameEn;

    @Column(name = "fiscal_year")
    private Integer fiscalYear;

    @Column(name = "fiscal_quarter")
    private Integer fiscalQuarter;
}

