package com.dilaw.mohe.wafar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WafarBalanceSummaryDto {
    private BigDecimal totalDeposits;
    private BigDecimal totalSpent;
    private BigDecimal currentBalance;
    private BigDecimal pendingAmount;
    private long transactionCount;
}
