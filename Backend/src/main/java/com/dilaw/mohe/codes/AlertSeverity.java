package com.dilaw.mohe.codes;

/** LU_ALERT_SEVERITY: scenario severities and the document-expiry priority ladder. */
@CodeList(value = "alert-severity", mode = CodeList.Mode.EXACT)
public final class AlertSeverity {

    private AlertSeverity() {
    }

    public static final String CRITICAL = "CRITICAL";
    public static final String HIGH = "HIGH";
    public static final String MEDIUM = "MEDIUM";
    public static final String LOW = "LOW";
}
