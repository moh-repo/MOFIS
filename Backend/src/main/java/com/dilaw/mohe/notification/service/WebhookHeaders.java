package com.dilaw.mohe.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

/**
 * The extra headers an administrator stores with a gateway webhook (SMS, WhatsApp),
 * as a JSON array {@code [{"key":"Authorization","value":"Bearer ..."}]}.
 */
final class WebhookHeaders {

    private static final Logger log = LoggerFactory.getLogger(WebhookHeaders.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WebhookHeaders() {
    }

    static void apply(HttpRequest.Builder builder, String headersJson, String what) {
        if (headersJson == null || headersJson.isBlank()) return;
        try {
            List<Map<String, String>> headers = MAPPER.readValue(headersJson, new TypeReference<>() {});
            for (Map<String, String> h : headers) {
                String key = h.get("key");
                String value = h.get("value");
                if (key != null && !key.isBlank() && value != null) {
                    builder.header(key, value);
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse {} webhook headers JSON: {}", what, e.getMessage());
        }
    }
}
