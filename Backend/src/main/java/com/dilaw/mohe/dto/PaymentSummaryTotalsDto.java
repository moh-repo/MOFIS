package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payment Summary's money cards over every row the filters select (round-6 QA R6-05).
 * Amounts are summed across currencies without conversion, as the page's rows are.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSummaryTotalsDto {

    private BigDecimal totalAmount;

    /** One entry per status code the rows carry; a null code is a row with no status. */
    private List<StatusAmount> byStatus;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusAmount {
        private String code;
        private BigDecimal amount;
    }
}
