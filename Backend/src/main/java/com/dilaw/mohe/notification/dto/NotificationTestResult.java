package com.dilaw.mohe.notification.dto;

/**
 * What a test send did (review R7).
 *
 * @param ok      the relay or webhook accepted the message
 * @param enabled the channel is switched on in these settings; real sends skip it when false
 * @param error   the relay's or webhook's own error when {@code ok} is false
 */
public record NotificationTestResult(boolean ok, boolean enabled, String error) {
}
