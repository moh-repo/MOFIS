package com.dilaw.mohe.codes;

/** LU_WAFAR_SPENDING_STATUS: wafar spending transactions (WafarSpendingRepository predicates). */
@CodeList(value = "wafar-spending-status", mode = CodeList.Mode.EXACT)
public final class WafarSpendingStatus {

    private WafarSpendingStatus() {
    }

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    /** Every status a transaction can hold; the transaction count binds this set. */
    public static final java.util.List<String> ALL = java.util.List.of(PENDING, APPROVED, REJECTED, CANCELLED);
}
