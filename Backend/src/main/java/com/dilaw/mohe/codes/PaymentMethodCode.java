package com.dilaw.mohe.codes;

/**
 * LU_PAYMENT_METHOD (V078): the payment summary derives one of these from what a
 * STUDENT_FINANCE row references — a bank, a cheque number, or neither — and
 * binds it into the query; names and icons come from the table.
 */
@CodeList(value = "payment-method", mode = CodeList.Mode.EXACT)
public final class PaymentMethodCode {

    private PaymentMethodCode() {
    }

    public static final String BANK_TRANSFER = "BANK_TRANSFER";
    public static final String CHECK = "CHECK";
    public static final String CASH = "CASH";
}
