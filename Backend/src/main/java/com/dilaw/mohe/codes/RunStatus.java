package com.dilaw.mohe.codes;

/** LU_RUN_STATUS (V074): SCHEDULED_REPORT.LAST_RUN_STATUS — the outcome of a scheduled run. */
@CodeList(value = "run-status", mode = CodeList.Mode.EXACT)
public final class RunStatus {

    private RunStatus() {
    }

    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
}
