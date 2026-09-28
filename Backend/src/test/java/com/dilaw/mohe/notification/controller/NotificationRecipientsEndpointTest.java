package com.dilaw.mohe.notification.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The recipients of one history row (V140): read at
 * {@code GET /api/v1/notifications/history/{id}/recipients}, behind the same
 * authority as the history itself. The history page's view depends on both.
 */
class NotificationRecipientsEndpointTest {

    @Test
    @DisplayName("GET history/{id}/recipients exists and needs NOTIFICATIONS.VIEW, like the history")
    void recipientsSitUnderTheHistory() throws Exception {
        Method recipients = NotificationController.class.getDeclaredMethod("getRecipients", Long.class);
        Method history = java.util.Arrays.stream(NotificationController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("getHistory")).findFirst().orElseThrow();

        assertThat(recipients.getAnnotation(GetMapping.class).value()).containsExactly("/history/{id}/recipients");
        assertThat(recipients.getAnnotation(PreAuthorize.class).value())
                .isEqualTo(history.getAnnotation(PreAuthorize.class).value());
    }
}
