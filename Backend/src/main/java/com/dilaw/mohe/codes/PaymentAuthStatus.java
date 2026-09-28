package com.dilaw.mohe.codes;

/** LU_PAYMENT_AUTH_STATUS (PaymentAuthService). */
@CodeList(value = "payment-auth-status", mode = CodeList.Mode.EXACT)
public final class PaymentAuthStatus {

    private PaymentAuthStatus() {
    }

    public static final String DRAFT = "DRAFT";
    public static final String APPROVED = "APPROVED";
    public static final String PAID = "PAID";
}
