package com.dilaw.mohe.notification.service;

import com.dilaw.mohe.codes.NotificationChannel;

/**
 * One person a batch addresses: who they are, and where each channel reaches
 * them. It used to be a bare {email, phone} pair, which was enough to send but
 * left nothing to record about who was written to (V140).
 *
 * @param type  STUDENT or EMPLOYEE (LU_NOTIFICATION_RECIPIENT_TYPE)
 * @param id    STUDENT_DATA.STUDENT_ID or UMSCORE.Users.Id, by type
 * @param name  as shown in the history; may be null for an unnamed record
 */
public record RecipientContact(String type, Long id, String name, String email, String phone) {

    /** The address a channel uses: the phone for SMS and WhatsApp, the e-mail otherwise. */
    public String addressFor(String channel) {
        return NotificationChannel.usesPhone(channel) ? phone : email;
    }
}
