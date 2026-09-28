package com.dilaw.mohe.util;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Value comparison for change detection between two JSON representations of an entity.
 *
 * Shared so the "did anything change?" check that gates approval creation and the
 * "what changed?" view shown to approvers cannot drift apart and disagree.
 */
public final class JsonValueComparator {

    private JsonValueComparator() {}

    /**
     * Treats null, missing, blank and zero as the same absence of a value.
     *
     * Clients normalize unset fields to 0 or "" while the column holds NULL; that
     * difference carries no intent to change and must not register as an edit.
     */
    public static boolean isEmptyValue(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode()
                || (node.isTextual() && node.asText().isBlank())
                || (node.isNumber() && node.asDouble() == 0d);
    }

    /** Equality under {@link #isEmptyValue} semantics, tolerant of string/number typing. */
    public static boolean valuesEquivalent(JsonNode a, JsonNode b) {
        boolean aEmpty = isEmptyValue(a);
        boolean bEmpty = isEmptyValue(b);
        if (aEmpty || bEmpty) {
            return aEmpty && bEmpty;
        }
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue()) == 0;
        }
        if (a.isBoolean() || b.isBoolean()) {
            return a.asBoolean() == b.asBoolean();
        }
        return a.asText().equals(b.asText());
    }

    /** Plain text for display; empty values render as an empty string. */
    public static String asDisplayText(JsonNode node) {
        if (isEmptyValue(node)) {
            return "";
        }
        /*
         * Whole numbers print in full rather than in scientific notation: Jackson renders a
         * large double as "1.762209230023E9", which is unreadable in a change list where the
         * value is an id or an epoch timestamp.
         */
        if (node.isNumber()) {
            return node.decimalValue().stripTrailingZeros().toPlainString();
        }
        return node.asText();
    }
}
