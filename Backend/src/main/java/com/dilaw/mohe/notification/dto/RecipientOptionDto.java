package com.dilaw.mohe.notification.dto;

/**
 * One match in the "send to one person" picker (review R4): a student or an
 * employee the caller may message. The phone number itself is not sent, only
 * whether there is one, so the screen can warn before an SMS goes nowhere.
 */
public record RecipientOptionDto(Long id, String name, String email, boolean hasEmail, boolean hasPhone) {
}
