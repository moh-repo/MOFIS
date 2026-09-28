package com.dilaw.mohe.notification.service;

import com.dilaw.mohe.notification.model.NotificationSettings;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Sends a WhatsApp message through an HTTP gateway configured in the notification
 * settings — the Meta WhatsApp Cloud API or any provider that takes a JSON POST.
 *
 * <p>Providers disagree on the body, so the administrator stores it as a template
 * with {@code {{to}}}, {@code {{toDigits}}}, {@code {{from}}}, {@code {{body}}} and
 * {@code {{bodyOneLine}}} placeholders. Each value is JSON-escaped as it is put in,
 * so the placeholders belong inside string quotes and a message can never break out
 * of its field. No free-text template means {@link #DEFAULT_BODY_TEMPLATE}, the
 * Cloud API text message.
 *
 * <p>Two kinds of message, because Meta allows a free-form text only inside the
 * 24-hour window after the recipient last wrote to the business number; outside it
 * the Cloud API refuses with error 131047 and wants a pre-approved template message.
 * The settings row's mode (V118) picks: {@link #MODE_TEXT} free text only,
 * {@link #MODE_TEMPLATE} always the approved template, {@link #MODE_AUTO} free text
 * first and the approved template when the window turns out to be closed.
 */
@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(toDigits|to|from|bodyOneLine|body)}}");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static final String MODE_TEXT = "TEXT";
    public static final String MODE_TEMPLATE = "TEMPLATE";
    public static final String MODE_AUTO = "AUTO";
    public static final Set<String> MODES = Set.of(MODE_TEXT, MODE_TEMPLATE, MODE_AUTO);

    public static final String DEFAULT_BODY_TEMPLATE =
            "{\"messaging_product\":\"whatsapp\",\"recipient_type\":\"individual\",\"to\":\"{{toDigits}}\","
                    + "\"type\":\"text\",\"text\":{\"preview_url\":false,\"body\":\"{{body}}\"}}";

    /**
     * The shape of a Cloud API template message with one body parameter. The name
     * and language are the ones registered in the WhatsApp Business account, so
     * this is offered as an example to edit, never used on its own.
     */
    public static final String EXAMPLE_APPROVED_TEMPLATE =
            "{\"messaging_product\":\"whatsapp\",\"recipient_type\":\"individual\",\"to\":\"{{toDigits}}\","
                    + "\"type\":\"template\",\"template\":{\"name\":\"mohe_notification\",\"language\":{\"code\":\"ar\"},"
                    + "\"components\":[{\"type\":\"body\",\"parameters\":[{\"type\":\"text\",\"text\":\"{{bodyOneLine}}\"}]}]}}";

    /** O-36: the gateway URL is administrator-configured; private and local targets are refused. */
    @org.springframework.beans.factory.annotation.Autowired
    private com.dilaw.mohe.config.OutboundHostPolicy outboundHostPolicy;

    /** @return null on success, otherwise the reason it failed */
    public String send(NotificationSettings cfg, String toPhone, String messageText) {
        if (cfg.getWhatsappWebhookUrl() == null || cfg.getWhatsappWebhookUrl().isBlank()) {
            return "WhatsApp gateway URL not configured";
        }
        String refused = outboundHostPolicy.refusalForUrl(cfg.getWhatsappWebhookUrl());
        if (refused != null) {
            return "WhatsApp gateway refused by outbound policy: " + refused;
        }
        String mode = modeOf(cfg);
        String approved = cfg.getWhatsappApprovedTemplate();
        boolean hasApproved = approved != null && !approved.isBlank();
        if (MODE_TEMPLATE.equals(mode)) {
            return hasApproved
                    ? post(cfg, toPhone, render(approved, toPhone, cfg.getWhatsappFromNumber(), messageText))
                    : "WhatsApp approved template not configured";
        }
        String error = post(cfg, toPhone, render(cfg.getWhatsappBodyTemplate(), toPhone, cfg.getWhatsappFromNumber(), messageText));
        if (error != null && MODE_AUTO.equals(mode) && hasApproved && windowClosed(error)) {
            String second = post(cfg, toPhone, render(approved, toPhone, cfg.getWhatsappFromNumber(), messageText));
            return second == null ? null : error + " | approved template: " + second;
        }
        return error;
    }

    /** The row's mode; an unknown or missing one is free text, V117's behaviour. */
    static String modeOf(NotificationSettings cfg) {
        String m = cfg.getWhatsappMessageMode();
        if (m == null) return MODE_TEXT;
        String upper = m.trim().toUpperCase(Locale.ROOT);
        return MODES.contains(upper) ? upper : MODE_TEXT;
    }

    /**
     * Whether the gateway refused a free text because the 24-hour customer-service
     * window is closed: Cloud API error 131047 ("Re-engagement message").
     */
    static boolean windowClosed(String error) {
        return error != null && (error.contains("131047") || error.toLowerCase(Locale.ROOT).contains("re-engagement"));
    }

    private String post(NotificationSettings cfg, String toPhone, String json) {
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.getWhatsappWebhookUrl().trim()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            WebhookHeaders.apply(reqBuilder, cfg.getWhatsappWebhookHeaders(), "WhatsApp");

            HttpResponse<String> resp = HTTP.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return null;
            }
            return "Gateway returned HTTP " + resp.statusCode() + ": " + resp.body();
        } catch (Exception e) {
            log.warn("Failed to send WhatsApp message to {}: {}", toPhone, e.getMessage());
            return e.getMessage();
        }
    }

    /** The request body for one message: the template with every placeholder filled and escaped. */
    static String render(String template, String to, String from, String body) {
        String t = (template == null || template.isBlank()) ? DEFAULT_BODY_TEMPLATE : template;
        String phone = to == null ? "" : to.trim();
        String text = body == null ? "" : body;
        Map<String, String> values = Map.of(
                "to", phone,
                "toDigits", phone.replaceAll("\\D", ""),
                "from", from == null ? "" : from.trim(),
                "body", text,
                "bodyOneLine", oneLine(text));
        // One pass, so a value that happens to contain "{{body}}" is never filled in again.
        Matcher m = PLACEHOLDER.matcher(t);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(escape(values.get(m.group(1)))));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * The message as one line, for a template parameter: Meta refuses a parameter
     * with a new line, a tab or more than four spaces in a row.
     */
    static String oneLine(String text) {
        return Arrays.stream(text.split("\\R"))
                .map(line -> line.replace('\t', ' ').replaceAll(" {2,}", " ").trim())
                .filter(line -> !line.isEmpty())
                .collect(Collectors.joining(" | "));
    }

    /**
     * Why a template cannot be saved, or null when it can: it must name the
     * recipient and the message, and must be JSON once filled in.
     */
    public static String templateProblem(String template) {
        return templateProblem(template, "WhatsApp body template");
    }

    public static String templateProblem(String template, String what) {
        if (template == null || template.isBlank()) return null;
        if (!template.contains("{{to}}") && !template.contains("{{toDigits}}")) {
            return "The " + what + " must contain {{to}} or {{toDigits}}";
        }
        if (!template.contains("{{body}}") && !template.contains("{{bodyOneLine}}")) {
            return "The " + what + " must contain {{body}} or {{bodyOneLine}}";
        }
        try {
            MAPPER.readTree(render(template, "+10000000000", "sender", "line \"one\"\nline two"));
            return null;
        } catch (JsonProcessingException e) {
            return "The " + what + " is not valid JSON once filled in; keep each placeholder inside quotes";
        }
    }

    /** A value as the inside of a JSON string literal. */
    private static String escape(String value) {
        try {
            String quoted = MAPPER.writeValueAsString(value);
            return quoted.substring(1, quoted.length() - 1);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
