package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One Wafar transaction as it appears on the statement.
 *
 * <p>Every lookup id here is paired with the name it resolves to. The report
 * audit ({@code scripts/audit_report_data.py}) fails a bean that exposes a
 * {@code *Id} with no name-bearing sibling, because such a column can only ever
 * print a number — and a statement that prints "category 3" is not a statement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WafarStatementRowDto {

    private Integer id;
    private LocalDate spendingDate;

    private Integer categoryId;
    /** Resolved for the requested language, so the template prints a name. */
    private String categoryName;

    private String referenceNumber;
    private String description;
    private String payeeName;

    private Integer studentId;
    private String studentName;

    /** PERSON, INSTITUTION, STUDENT — as recorded on the transaction. */
    private String beneficiaryType;

    /** The attaché holding this Wafar. Null on rows written before it was recorded. */
    private Integer studyCountryInId;
    private String studyCountryName;

    private BigDecimal amount;
    /** The status code (APPROVED, PENDING…); the totals are summed on it. */
    private String status;
    /** The status named in the requested language, for the printed statement (live QA M14). */
    private String statusLabel;
}
