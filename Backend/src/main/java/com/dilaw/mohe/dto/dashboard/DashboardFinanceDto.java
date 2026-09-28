package com.dilaw.mohe.dto.dashboard;

import com.dilaw.mohe.dto.SimpleDataPointDto;
import com.dilaw.mohe.wafar.dto.WafarBalanceSummaryDto;

import java.util.List;

/**
 * The Finance section of the landing page, in the caller's country scope.
 *
 * @param totalDisbursed      sum of {@code STUDENT_FINANCE.NET_AMOUNT}, all time
 * @param averageDisbursement average {@code NET_AMOUNT} per finance row
 * @param byYear              net amount per received-year, last six, oldest first
 * @param wafar               the attaché's Wafar balance (deposits, spent, balance, pending)
 */
public record DashboardFinanceDto(
        double totalDisbursed,
        double averageDisbursement,
        List<SimpleDataPointDto> byYear,
        WafarBalanceSummaryDto wafar) {
}
