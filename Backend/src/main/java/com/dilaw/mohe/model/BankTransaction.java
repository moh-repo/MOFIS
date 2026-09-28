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
@Subselect("""
    SELECT
        CAST(ROW_NUMBER() OVER (ORDER BY COALESCE(sf.STUDY_CHECK_DATE, sf.STUDY_FINANCE_MIDATE) DESC, sf.STUDENT_FIN_ID) AS BIGINT) AS transaction_id,
        CAST(COALESCE(sf.STUDY_CHECK_DATE, sf.STUDY_FINANCE_MIDATE) AS DATE) AS transaction_date,
        CAST(COALESCE(sfrs.STUDY_FIN_RECEIVED_STATUS_NAME, 'Unknown') AS NVARCHAR(255)) AS transaction_type,
        -- English sibling for an English report (QA B6); the Arabic column is unchanged.
        CAST(COALESCE(NULLIF(sfrs.STUDY_FIN_RECEIVED_STATUS_NAME_EN, ''), sfrs.STUDY_FIN_RECEIVED_STATUS_NAME, 'Unknown') AS NVARCHAR(255)) AS transaction_type_en,
        CASE
            WHEN COALESCE(sfrs.STUDY_FIN_RECEIVED_STATUS_NAME, '') LIKE '%Credit%' THEN 'Credit'
            WHEN COALESCE(sfrs.STUDY_FIN_RECEIVED_STATUS_NAME, '') LIKE '%Debit%' THEN 'Debit'
            WHEN COALESCE(srws.STUDY_RECEIVED_WHOM_STATUS_NAME, '') LIKE '%Deposit%' THEN 'Credit'
            WHEN COALESCE(srws.STUDY_RECEIVED_WHOM_STATUS_NAME, '') LIKE '%Withdrawal%' THEN 'Debit'
            WHEN COALESCE(sfrs.STUDY_FIN_RECEIVED_STATUS_NAME, '') LIKE '%Paid%' THEN 'Debit'
            WHEN COALESCE(sfrs.STUDY_FIN_RECEIVED_STATUS_NAME, '') LIKE '%Received%' THEN 'Credit'
            -- No ELSE 'Debit'. Every branch above reads a recorded status; the
            -- fallback did not, so a row whose status matched nothing was still
            -- labelled a debit. On a bank report that is a direction of money
            -- movement asserted from no evidence.
            ELSE NULL
        END AS transaction_direction,
        CAST(COALESCE(
            NULLIF(CAST(sf.STUDY_CHECK_DETAILS AS NVARCHAR(MAX)), ''),
            NULLIF(srws.STUDY_RECEIVED_WHOM_STATUS_NAME, ''),
            NULLIF(sfrs.STUDY_FIN_RECEIVED_STATUS_NAME, ''),
            'Transaction'
        ) AS NVARCHAR(1000)) AS transaction_description,
        CAST(COALESCE(sf.STUDENT_FIN_RECIEVED_AMOUNT, 0) AS DECIMAL(18, 2)) AS transaction_amount,
        -- Resolved from the currency the record carries. This used to be the
        -- literal 'USD', which presented an assumption as data: STUDENT_FINANCE
        -- has a STUDY_CURRENCY_IN_ID and a lookup to go with it. Where the id is
        -- not recorded the column is now empty rather than asserting a currency.
        CAST(NULLIF(cur.STUDY_CURRENCY_IN_NAME, '') AS NVARCHAR(100)) AS transaction_currency,
        CAST(NULLIF(COALESCE(cur.STUDY_CURRENCY_IN_NAME_EN, cur.STUDY_CURRENCY_IN_NAME), '') AS NVARCHAR(100)) AS transaction_currency_en,
        CAST(sf.STUDY_CHECK_ID AS NVARCHAR(255)) AS reference_number,
        CAST(bank.STUDY_FINANCE_BANKE_NAME AS NVARCHAR(255)) AS bank_name,
        CAST(COALESCE(bank.STUDY_FINANCE_BANKE_NAME_EN, bank.STUDY_FINANCE_BANKE_NAME) AS NVARCHAR(255)) AS bank_name_en,
        CAST(branch.STUDY_FINANCE_BANK_BRANCH_NAME AS NVARCHAR(255)) AS bank_branch,
        CAST(COALESCE(branch.STUDY_FINANCE_BANK_BRANCH_NAME_EN, branch.STUDY_FINANCE_BANK_BRANCH_NAME) AS NVARCHAR(255)) AS bank_branch_en,
        sf.STUDENT_ID AS student_id,
        CAST(sd.STUDENT_NAME AS NVARCHAR(255)) AS beneficiary_name,
        CAST(sf.STUDENT_ACCOUNT_NUMBER AS NVARCHAR(255)) AS beneficiary_account
    FROM MOHECORE.STUDENT_FINANCE sf
    LEFT JOIN MOHECORE.STUDY_FIN_RECEIVED_STATUS sfrs ON sf.STUDY_FIN_RECIEVED_STATUS = sfrs.STUDY_FIN_RECEIVED_STATUS_ID
    LEFT JOIN MOHECORE.STUDY_RECEIVED_WHOM_STATUS srws ON sf.STUDY_RECEIVED_WHOM_STATUS_ID = srws.STUDY_RECEIVED_WHOM_STATUS_ID
    LEFT JOIN MOHECORE.STUDY_FINANCE_BANK bank ON sf.STUDY_FINANCE_BANKE_ID = bank.STUDY_FINANCE_BANKE_ID
    LEFT JOIN MOHECORE.STUDY_FINANCE_BANK_BRANCH branch ON sf.STUDY_FINANCE_BANK_BRANCH_ID = branch.STUDY_FINANCE_BANK_BRANCH_ID
    LEFT JOIN MOHECORE.STUDY_CURRENCY_IN cur ON sf.STUDY_CURRENCY_IN_ID = cur.STUDY_CURRENCY_IN_ID
    LEFT JOIN MOHECORE.STUDENT_DATA sd ON sf.STUDENT_ID = sd.STUDENT_ID
    WHERE sf.STUDENT_FIN_RECIEVED_AMOUNT IS NOT NULL
      AND ISNULL(sd.IS_DELETED, 0) = 0
""")
@Synchronize({
        "STUDENT_FINANCE",
        "STUDY_FIN_RECEIVED_STATUS",
        "STUDY_RECEIVED_WHOM_STATUS",
        "STUDY_FINANCE_BANK",
        "STUDY_FINANCE_BANK_BRANCH",
        "STUDY_CURRENCY_IN",
        "STUDENT_DATA"
})
public class BankTransaction {

    @Id
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    @Column(name = "transaction_type")
    private String transactionType;

    /** The same name in English; resolved by BilingualRows for an EN render (QA B6). */
    @Column(name = "transaction_type_en")
    private String transactionTypeEn;

@Column(name = "transaction_direction")
private String transactionDirection;

    @Column(name = "transaction_description")
    private String transactionDescription;

    @Column(name = "transaction_amount")
    private BigDecimal transactionAmount;

    @Column(name = "transaction_currency")
    private String transactionCurrency;

    @Column(name = "transaction_currency_en")
    private String transactionCurrencyEn;

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_name_en")
    private String bankNameEn;

    @Column(name = "bank_branch")
    private String bankBranch;

    @Column(name = "bank_branch_en")
    private String bankBranchEn;

    @Column(name = "student_id")
    private Integer studentId;

    @Column(name = "beneficiary_name")
    private String beneficiaryName;

    @Column(name = "beneficiary_account")
    private String beneficiaryAccount;
}

