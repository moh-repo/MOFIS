package com.dilaw.mohe.notification.service;

import com.dilaw.mohe.notification.dto.SendMessageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Which channels a send request asks for: the list, or the legacy single code. */
class NotificationChannelsTest {

    private static SendMessageRequest req(List<String> channels, String channel) {
        SendMessageRequest r = new SendMessageRequest();
        r.setChannels(channels);
        r.setChannel(channel);
        return r;
    }

    @Test
    @DisplayName("the list wins, in display order, duplicates collapsed, BOTH expanded")
    void list() {
        assertThat(NotificationService.requestedChannels(req(List.of("whatsapp", "EMAIL", "BOTH"), "SMS")))
                .containsExactly("EMAIL", "SMS", "WHATSAPP");
    }

    @Test
    @DisplayName("an old client's single channel still works; BOTH is e-mail and SMS")
    void legacy() {
        assertThat(NotificationService.requestedChannels(req(null, "BOTH"))).containsExactly("EMAIL", "SMS");
        assertThat(NotificationService.requestedChannels(req(List.of(), "SMS"))).containsExactly("SMS");
    }

    @Test
    @DisplayName("no channel or an unknown one is a 400")
    void refused() {
        assertThatThrownBy(() -> NotificationService.requestedChannels(req(null, null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("at least one channel");
        assertThatThrownBy(() -> NotificationService.requestedChannels(req(List.of("SLACK"), null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("unknown channel");
    }
}
