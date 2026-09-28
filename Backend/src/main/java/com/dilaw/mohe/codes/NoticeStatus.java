package com.dilaw.mohe.codes;

/** LU_NOTICE_STATUS (V074): MOHE_ALERTS.alerts.alert_status — the system notices on the dashboard. */
@CodeList(value = "notice-status", mode = CodeList.Mode.EXACT)
public final class NoticeStatus {

    private NoticeStatus() {
    }

    public static final String ACTIVE = "ACTIVE";
    public static final String RESOLVED = "RESOLVED";
    public static final String DISMISSED = "DISMISSED";
}
