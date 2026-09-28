package com.dilaw.mohe.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * How a student-portal session token is stored.
 *
 * <p>The token is a 128-bit random value handed to the browser. Until security
 * plan O-06 (6 Sep 2026) the same value was the primary lookup key in
 * {@code STUDENTCORE.student_sessions} and {@code MOHECORE.StudentPortalSession},
 * so a read of either table — a backup, a report login, an RLS-exempt
 * administrator — yielded every live portal session. The tables now hold the
 * SHA-256 of the token, hex encoded; the token itself exists only in the
 * browser and in flight. Looking one up hashes the presented value first.
 *
 * <p>Unsalted on purpose: the input is 128 bits of entropy, so a rainbow table
 * is not a threat, and a salt would defeat the indexed lookup.
 * {@code V086__hash_portal_session_tokens.sql} converts the existing rows with
 * {@code HASHBYTES('SHA2_256', ...)}, which produces the same bytes.
 */
public final class PortalTokens {

    private PortalTokens() {
    }

    /** SHA-256 of the token's UTF-8 bytes, as 64 lower-case hex characters. */
    public static String hash(String rawToken) {
        if (rawToken == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
