package com.dilaw.mohe.codes;

/** LU_FISCAL_PERIOD_STATUS (V073): FiscalPeriod.status, the V040 CHECK constraint. */
@CodeList(value = "fiscal-period-status", mode = CodeList.Mode.EXACT)
public final class FiscalPeriodStatus {

    private FiscalPeriodStatus() {
    }

    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";
}
