package com.dilaw.mohe.codes;

/** LU_NOTIFICATION_SEND_STATUS (V073): NotificationService outcome per batch. */
@CodeList(value = "notification-send-status", mode = CodeList.Mode.EXACT)
public final class NotificationSendStatus {

    private NotificationSendStatus() {
    }

    public static final String PENDING = "PENDING";
    public static final String SENT = "SENT";
    public static final String PARTIAL = "PARTIAL";
    public static final String FAILED = "FAILED";
}
