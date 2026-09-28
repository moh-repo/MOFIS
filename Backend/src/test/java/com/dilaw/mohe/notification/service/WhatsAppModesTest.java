package com.dilaw.mohe.notification.service;

import com.dilaw.mohe.config.OutboundHostPolicy;
import com.dilaw.mohe.notification.model.NotificationSettings;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three WhatsApp message modes (V118), against a local gateway that answers
 * like the Cloud API: a free text to a recipient outside the 24-hour window is
 * refused with error 131047, a template message is accepted.
 */
class WhatsAppModesTest {

    private HttpServer gateway;
    private final List<String> received = Collections.synchronizedList(new ArrayList<>());
    private WhatsAppService service;
    private NotificationSettings cfg;

    @BeforeEach
    void setUp() throws IOException {
        gateway = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gateway.createContext("/messages", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(body);
            // A template message also has "type":"text" inside its parameters.
            boolean freeText = !body.contains("\"type\":\"template\"");
            String answer = freeText
                    ? "{\"error\":{\"message\":\"Re-engagement message\",\"code\":131047}}"
                    : "{\"messages\":[{\"id\":\"wamid.test\"}]}";
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(freeText ? 400 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        gateway.start();

        service = new WhatsAppService();
        ReflectionTestUtils.setField(service, "outboundHostPolicy", new OutboundHostPolicy(true));
        cfg = new NotificationSettings();
        cfg.setWhatsappEnabled(true);
        cfg.setWhatsappWebhookUrl("http://127.0.0.1:" + gateway.getAddress().getPort() + "/messages");
        cfg.setWhatsappApprovedTemplate(WhatsAppService.EXAMPLE_APPROVED_TEMPLATE);
    }

    @AfterEach
    void tearDown() {
        gateway.stop(0);
    }

    @Test
    @DisplayName("TEXT: free text only — outside the window it fails and says why")
    void textOnly() {
        cfg.setWhatsappMessageMode(WhatsAppService.MODE_TEXT);
        String error = service.send(cfg, "+962790000000", "Hello");
        assertThat(error).contains("131047");
        assertThat(received).hasSize(1);
    }

    @Test
    @DisplayName("TEMPLATE: always the approved template, with the message as one line")
    void templateOnly() {
        cfg.setWhatsappMessageMode(WhatsAppService.MODE_TEMPLATE);
        assertThat(service.send(cfg, "+962790000000", "Line one\nLine two")).isNull();
        assertThat(received).hasSize(1);
        assertThat(received.get(0)).contains("\"type\":\"template\"").contains("Line one | Line two");
    }

    @Test
    @DisplayName("AUTO: free text first, then the approved template when the window is closed")
    void autoFallsBack() {
        cfg.setWhatsappMessageMode(WhatsAppService.MODE_AUTO);
        assertThat(service.send(cfg, "+962790000000", "Hello")).isNull();
        assertThat(received).hasSize(2);
        assertThat(received.get(0)).doesNotContain("\"type\":\"template\"");
        assertThat(received.get(1)).contains("\"type\":\"template\"");
    }

    @Test
    @DisplayName("AUTO does not retry an error that is not the closed window")
    void autoOnlyOnWindowError() {
        cfg.setWhatsappMessageMode(WhatsAppService.MODE_AUTO);
        cfg.setWhatsappWebhookUrl(cfg.getWhatsappWebhookUrl().replace("/messages", "/missing"));
        assertThat(service.send(cfg, "+962790000000", "Hello")).contains("404");
        assertThat(received).isEmpty();
    }

    @Test
    @DisplayName("TEMPLATE with no approved template is a clear failure, not a free text")
    void templateMissing() {
        cfg.setWhatsappMessageMode(WhatsAppService.MODE_TEMPLATE);
        cfg.setWhatsappApprovedTemplate(null);
        assertThat(service.send(cfg, "+962790000000", "Hello")).contains("approved template not configured");
        assertThat(received).isEmpty();
    }

    @Test
    @DisplayName("an unknown or missing mode is free text")
    void unknownMode() {
        cfg.setWhatsappMessageMode(null);
        assertThat(WhatsAppService.modeOf(cfg)).isEqualTo(WhatsAppService.MODE_TEXT);
        cfg.setWhatsappMessageMode("auto");
        assertThat(WhatsAppService.modeOf(cfg)).isEqualTo(WhatsAppService.MODE_AUTO);
        cfg.setWhatsappMessageMode("SOMETHING");
        assertThat(WhatsAppService.modeOf(cfg)).isEqualTo(WhatsAppService.MODE_TEXT);
    }
}
