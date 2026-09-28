package com.dilaw.mohe.codes;

/** LU_ALERT_TYPE (V073): the kind of a system notice in MOHE_ALERTS.alerts. */
@CodeList(value = "alert-type", mode = CodeList.Mode.EXACT)
public final class AlertType {

    private AlertType() {
    }

    public static final String INFO = "INFO";
    public static final String WARNING = "WARNING";
    public static final String ERROR = "ERROR";
    public static final String SUCCESS = "SUCCESS";
}
