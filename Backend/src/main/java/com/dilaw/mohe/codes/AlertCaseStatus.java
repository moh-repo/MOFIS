package com.dilaw.mohe.codes;

/** LU_ALERT_STATUS: the alert-case state machine (AlertEventCaseService). */
@CodeList(value = "alert-status", mode = CodeList.Mode.EXACT)
public final class AlertCaseStatus {

    private AlertCaseStatus() {
    }

    public static final String OPEN = "OPEN";
    public static final String ACKNOWLEDGED = "ACKNOWLEDGED";
    public static final String RESOLVED = "RESOLVED";
    public static final String DISMISSED = "DISMISSED";
}
