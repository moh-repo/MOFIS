package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One attaché's accounting position for a fiscal year, in one currency.
 *
 * <p>This is the trial-balance line the accounting module never had a document
 * for: what the attaché opened with, what was transferred to it, what it
 * disbursed and returned, what Wafar is holding, and what is left.
 *
 * <p>The row is per <b>attaché and currency</b>, not per attaché. An attaché
 * holding USD and SAR has two lines, and they are never added together — the
 * currency dimension is the one the Wafar balance card still loses, and this
 * report is not going to repeat that.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingPositionRowDto {

    private Integer fiscalYearId;
    private String fiscalYearName;

    private Integer studyCountryInId;
    private String studyCountryName;

    private Integer currencyId;
    private String currencyName;

    private BigDecimal openingBalance;
    private BigDecimal transferred;
    private BigDecimal disbursed;
    private BigDecimal returned;

    private BigDecimal wafarEarmarked;
    private BigDecimal wafarSpent;
    private BigDecimal wafarBalance;

    /** Computed by SQL Server, so it is read back rather than derived here. */
    private BigDecimal available;

    /** Allocations raised for this year, attaché and currency — count and total. */
    private long allocationCount;
    private BigDecimal allocatedAmount;
}
