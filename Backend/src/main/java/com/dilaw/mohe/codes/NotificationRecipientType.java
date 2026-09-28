package com.dilaw.mohe.codes;

/** LU_NOTIFICATION_RECIPIENT_TYPE: who a notification batch addresses (NotificationService). */
@CodeList(value = "notification-recipient-type", mode = CodeList.Mode.SUBSET)
public final class NotificationRecipientType {

    private NotificationRecipientType() {
    }

    public static final String STUDENT = "STUDENT";
    public static final String EMPLOYEE = "EMPLOYEE";
}
