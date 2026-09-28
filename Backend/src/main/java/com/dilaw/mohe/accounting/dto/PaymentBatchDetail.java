package com.dilaw.mohe.accounting.dto;

import java.util.List;

/** A batch and every line in it. */
public record PaymentBatchDetail(PaymentBatchSummary batch, List<PaymentBatchLineView> lines) {
}
