package com.dilaw.mohe.codes;

/** LU_WAFAR_RECONCILIATION_STATUS (WafarReconciliationService). */
@CodeList(value = "wafar-reconciliation-status", mode = CodeList.Mode.EXACT)
public final class WafarReconciliationStatus {

    private WafarReconciliationStatus() {
    }

    public static final String PENDING = "PENDING";
    public static final String MATCHED = "MATCHED";
    public static final String DISCREPANCY = "DISCREPANCY";
    public static final String RESOLVED = "RESOLVED";
}
