package com.dilaw.mohe.config;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one place the password hash strength is decided.
 *
 * <p>Seven classes each did {@code new BCryptPasswordEncoder()} — cost 10, the
 * library default — so raising the cost meant finding all seven. Security plan
 * O-08 (6 Sep 2026) puts it here at cost 12, the current OWASP recommendation:
 * roughly four times the work per guess for an attacker with a stolen hash,
 * still well under a third of a second to verify on the application server.
 *
 * <p>Existing hashes are not invalidated. {@link #needsRehash} lets the login
 * path re-encode a password at the new cost the next time it is presented and
 * verified, so the upgrade completes account by account with no forced resets.
 */
public final class Passwords {

    /** BCrypt work factor for every new hash. */
    public static final int COST = 12;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(COST);

    /** {@code $2a$10$...} — the cost is the two digits after the second dollar. */
    private static final Pattern COST_IN_HASH = Pattern.compile("^\\$2[aby]\\$(\\d{2})\\$");

    private Passwords() {
    }

    public static BCryptPasswordEncoder encoder() {
        return ENCODER;
    }

    /**
     * Whether a stored hash was made with a lower cost than {@link #COST} (or is
     * not a BCrypt hash at all), and should be re-encoded now that the plaintext
     * is available.
     */
    public static boolean needsRehash(String storedHash) {
        if (storedHash == null) return false;
        Matcher m = COST_IN_HASH.matcher(storedHash);
        if (!m.find()) return true;
        return Integer.parseInt(m.group(1)) < COST;
    }
}
