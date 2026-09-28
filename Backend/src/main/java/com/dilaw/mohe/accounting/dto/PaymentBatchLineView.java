package com.dilaw.mohe.accounting.dto;

import java.math.BigDecimal;

/** One line of a batch, with the student's name resolved so the dialog need not. */
public record PaymentBatchLineView(
        Integer id,
        Integer studentId,
        String studentName,
        BigDecimal entitledAmount,
        BigDecimal disbursedAmount,
        String heldReason) {
}
