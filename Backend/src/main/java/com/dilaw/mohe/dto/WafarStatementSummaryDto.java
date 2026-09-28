package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * The figures at the head of the Wafar statement, plus a per-category breakdown.
 *
 * <p>The totals come from {@code WafarBalanceService}, so the statement and the
 * balance card on the Wafar screen can never disagree — they are the same
 * numbers, scoped the same way.
 *
 * <p><b>Currency:</b> amounts are summed across USD, GBP and SAR as though they
 * were one unit, exactly as the balance card does. That is a known gap that
 * belongs with the FX work; the statement carries a footnote saying so rather
 * than presenting a mixed-currency total as though it were meaningful.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WafarStatementSummaryDto {

    private BigDecimal totalDeposits;
    private BigDecimal totalSpent;
    private BigDecimal currentBalance;
    private BigDecimal pendingAmount;
    private long transactionCount;

    private List<CategoryTotal> categoryTotals;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryTotal {
        private Integer categoryId;
        private String categoryName;
        private long transactionCount;
        private BigDecimal approvedAmount;
        private BigDecimal pendingAmount;
        private BigDecimal totalAmount;
    }
}
