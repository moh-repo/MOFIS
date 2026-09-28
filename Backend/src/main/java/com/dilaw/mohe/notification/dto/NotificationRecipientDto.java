package com.dilaw.mohe.notification.dto;

import java.time.Instant;

/** One recipient of one history row, as the history page's view lists them (V140). */
public record NotificationRecipientDto(
        Long id,
        String recipientType,
        Long recipientId,
        String name,
        String address,
        String status,
        String error,
        Instant sentAt) {
}
