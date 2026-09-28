package com.dilaw.mohe.notification.service;

import com.dilaw.mohe.notification.model.NotificationSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);
    /** O-36: the webhook URL is administrator-configured; private and local targets are refused. */
    @org.springframework.beans.factory.annotation.Autowired
    private com.dilaw.mohe.config.OutboundHostPolicy outboundHostPolicy;

    private String requireAllowed(String url) {
        String refused = outboundHostPolicy.refusalForUrl(url);
        if (refused != null) {
            throw new IllegalStateException("SMS webhook refused by outbound policy: " + refused);
        }
        return url;
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Sends an SMS via the configured generic HTTP webhook.
     * The webhook receives a JSON body: {"to":"<phone>","from":"<from>","body":"<text>"}
     * @return null on success, error message on failure
     */
    public String send(NotificationSettings cfg, String toPhone, String messageText) {
        if (cfg.getSmsWebhookUrl() == null || cfg.getSmsWebhookUrl().isBlank()) {
            return "SMS webhook URL not configured";
        }
        try {
            Map<String, String> payload = Map.of(
                    "to", toPhone,
                    "from", cfg.getSmsFromNumber() != null ? cfg.getSmsFromNumber() : "",
                    "body", messageText
            );
            String json = MAPPER.writeValueAsString(payload);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(requireAllowed(cfg.getSmsWebhookUrl())))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json");

            // Attach any extra headers stored as JSON array: [{"key":"...","value":"..."}]
            WebhookHeaders.apply(reqBuilder, cfg.getSmsWebhookHeaders(), "SMS");

            String method = cfg.getSmsWebhookMethod() != null ? cfg.getSmsWebhookMethod().toUpperCase() : "POST";
            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
            reqBuilder.method(method, body);

            HttpResponse<String> resp = HTTP.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return null;
            }
            return "Webhook returned HTTP " + resp.statusCode() + ": " + resp.body();
        } catch (Exception e) {
            log.warn("Failed to send SMS to {}: {}", toPhone, e.getMessage());
            return e.getMessage();
        }
    }
}
