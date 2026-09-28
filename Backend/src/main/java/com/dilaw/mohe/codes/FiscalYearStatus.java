package com.dilaw.mohe.codes;

/** LU_FISCAL_YEAR_STATUS (V073): FiscalYear.status, the V040 CHECK constraint. */
@CodeList(value = "fiscal-year-status", mode = CodeList.Mode.EXACT)
public final class FiscalYearStatus {

    private FiscalYearStatus() {
    }

    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";
    public static final String LOCKED = "LOCKED";
}
