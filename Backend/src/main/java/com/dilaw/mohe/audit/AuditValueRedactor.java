package com.dilaw.mohe.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks credentials in the values the audit triggers record (review M1).
 *
 * <p>The UMSCORE.Users trigger copies the whole row, so every login wrote the
 * bcrypt hash into OLD_VALUES and NEW_VALUES, and the audit screen showed it to
 * anyone holding AUDIT.VIEW. The trail itself is not rewritten (it is evidence);
 * what leaves the server is masked.
 *
 * <p>A key is masked when its name mentions a password, secret, token, salt, hash
 * or one-time code, unless it is plainly metadata about one: {@code PasswordChangedAt},
 * {@code TotpEnabled} and {@code FailedLoginAttempts} say when or whether, not what.
 */
public final class AuditValueRedactor {

    public static final String MASK = "***";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(password|passwd|secret|token|salt|hash|otp|api_?key|private_?key|credential)");
    private static final Pattern METADATA = Pattern.compile(
            "(?i).*(at|date|time|attempts|count|enabled|expiry|expires|until|stage|policy|length)$");
    /** "Key": value, for a value that is not valid JSON as a whole. */
    private static final Pattern PAIR = Pattern.compile(
            "\"([^\"\\\\]+)\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|[^,}\\]]+)");

    private AuditValueRedactor() {}

    public static boolean isSensitive(String key) {
        return key != null && SENSITIVE.matcher(key).find() && !METADATA.matcher(key).matches();
    }

    /** {@code json} with every sensitive value replaced by {@value #MASK}; null in, null out. */
    public static String redact(String json) {
        if (json == null || json.isBlank()) return json;
        try {
            JsonNode tree = MAPPER.readTree(json);
            if (tree == null || !(tree.isObject() || tree.isArray())) return redactText(json);
            return MAPPER.writeValueAsString(walk(tree));
        } catch (Exception notJson) {
            return redactText(json);
        }
    }

    private static JsonNode walk(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            List<String> names = new ArrayList<>();
            obj.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode value = obj.get(name);
                if (isSensitive(name) && value != null && !value.isNull()) {
                    obj.set(name, TextNode.valueOf(MASK));
                } else if (value != null && value.isTextual() && looksLikeJson(value.asText())) {
                    // ApprovalRequests keeps the pending record as JSON text inside the row.
                    obj.set(name, TextNode.valueOf(redact(value.asText())));
                } else if (value != null && value.isContainerNode()) {
                    walk(value);
                }
            }
        } else if (node instanceof ArrayNode arr) {
            for (Iterator<JsonNode> it = arr.elements(); it.hasNext(); ) {
                walk(it.next());
            }
        }
        return node;
    }

    private static boolean looksLikeJson(String s) {
        String t = s.strip();
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
    }

    /** Best effort for a value the trigger truncated or wrote as something other than JSON. */
    private static String redactText(String text) {
        Matcher m = PAIR.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String replacement = isSensitive(m.group(1))
                    ? "\"" + m.group(1) + "\":\"" + MASK + "\""
                    : m.group();
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
