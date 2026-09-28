package com.dilaw.mohe.accounting.dto;

import java.math.BigDecimal;

/**
 * A student finance row of the quarter, offered as a batch line.
 *
 * <p>{@code entitledAmount} is what the row says the student was due;
 * {@code netAmount} is what was actually paid out — the persisted
 * {@code received - wafar}, the same rule V045 derived the historical lines by.
 * {@code alreadyPaid} is true when a non-cancelled batch of this quarter and
 * attaché already carries the student in full, so the screen can leave those
 * unticked by default rather than paying twice.
 */
public record FinanceCandidate(
        Integer studentId,
        String studentName,
        BigDecimal entitledAmount,
        BigDecimal wafarAmount,
        BigDecimal netAmount,
        Integer currencyId,
        boolean alreadyPaid) {
}
