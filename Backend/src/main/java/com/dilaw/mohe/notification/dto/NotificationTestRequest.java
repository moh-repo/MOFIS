package com.dilaw.mohe.notification.dto;

/**
 * A test message through saved notification settings (review R7).
 *
 * @param countryId the attaché whose settings to use; null for the ministry-wide row
 * @param channel   EMAIL, SMS or WHATSAPP
 * @param to        the address or number to send it to
 */
public record NotificationTestRequest(Integer countryId, String channel, String to) {
}
