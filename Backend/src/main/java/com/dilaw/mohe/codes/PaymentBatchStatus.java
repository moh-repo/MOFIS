package com.dilaw.mohe.codes;

/** LU_PAYMENT_BATCH_STATUS (V073): PaymentBatch.status, the V042 CHECK constraint. */
@CodeList(value = "payment-batch-status", mode = CodeList.Mode.EXACT)
public final class PaymentBatchStatus {

    private PaymentBatchStatus() {
    }

    public static final String OPEN = "OPEN";
    public static final String APPROVED = "APPROVED";
    public static final String DISBURSED = "DISBURSED";
    public static final String CLOSED = "CLOSED";
    public static final String CANCELLED = "CANCELLED";
}
