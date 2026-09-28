package com.dilaw.mohe.ums.service;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The password rules, in one place, for staff and for the student portal.
 *
 * <p>Security plan O-25 (6 Sep 2026). Until now the staff floor was eight
 * characters with a letter and a digit ({@code UmsUserService.validatePasswordStrength})
 * and the portal's was six characters, checked in four separate places with
 * four copies of the same {@code if}. The rules are now:
 *
 * <ol>
 *   <li>at least {@link #STAFF_MIN} characters for staff, {@link #PORTAL_MIN}
 *       for students;
 *   <li>at least one letter (any script) and one digit;
 *   <li>must not contain the account's username, when that is four or more
 *       characters — {@code jo_admin2026} is not a password;
 *   <li>must not be one of the common passwords in
 *       {@code /common-passwords.txt}, compared case-insensitively and with
 *       trailing digits stripped, so {@code Password123} fails along with
 *       {@code password}.
 * </ol>
 *
 * <p>Password history (the last five) is checked by the caller that has the
 * user's hashes — {@code UmsUserService.changePassword} — because comparing
 * against BCrypt hashes needs the encoder, not a rule.
 *
 * <p>Existing passwords are not invalidated; the rules apply to new and
 * changed ones. {@link #problem} renders the broken rule in English for the
 * callers whose messages are English; {@link #violation} gives the rule as a
 * message key with its arguments, so a caller that answers in the reader's
 * language (the student portal — live QA L12, 24 Sep 2026) can render it
 * through the {@code MessageSource}. The frontend mirrors the same rules in
 * its validators.
 */
public final class PasswordPolicy {

    /**
     * The rule a password breaks: a {@code messages.properties} key, its
     * arguments, and the English rendering the key falls back to.
     */
    public record Violation(String key, Object[] args, String english) {
        /** The rule in {@code locale}, or in English when no message source is at hand. */
        public String localized(MessageSource messages, Locale locale) {
            if (messages == null || locale == null) return english;
            return messages.getMessage(key, args, english, locale);
        }
    }

    public static final int STAFF_MIN = 12;
    public static final int PORTAL_MIN = 10;

    /** Below this many characters a username is too short to be a meaningful substring test. */
    private static final int USERNAME_MIN_FOR_CONTAINS = 4;

    private static final Set<String> COMMON = loadCommon();

    private PasswordPolicy() {
    }

    /**
     * Throws {@link IllegalArgumentException} — which {@code GlobalExceptionHandler}
     * returns as a 400 — when the password breaks a rule.
     *
     * @param password  the candidate
     * @param username  the account's login name, or null when unknown
     * @param minLength {@link #STAFF_MIN} or {@link #PORTAL_MIN}
     */
    public static void check(String password, String username, int minLength) {
        String problem = problem(password, username, minLength);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
    }

    /** The same, for services that speak in {@link ResponseStatusException}. */
    public static void checkOrBadRequest(String password, String username, int minLength) {
        String problem = problem(password, username, minLength);
        if (problem != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
        }
    }

    /**
     * The same, with the reason in {@code locale} (English when {@code messages}
     * is null, as in a unit test that builds the service without a context).
     */
    public static void checkOrBadRequest(String password, String username, int minLength,
                                         MessageSource messages, Locale locale) {
        Violation violation = violation(password, username, minLength);
        if (violation != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, violation.localized(messages, locale));
        }
    }

    /** @return the first rule broken, in English, or null when the password is acceptable */
    public static String problem(String password, String username, int minLength) {
        Violation violation = violation(password, username, minLength);
        return violation == null ? null : violation.english();
    }

    /** @return the first rule broken, as a message key with its English fallback, or null */
    public static Violation violation(String password, String username, int minLength) {
        if (password == null || password.length() < minLength) {
            return new Violation("password.tooShort", new Object[] {String.valueOf(minLength)},
                    "Password must be at least " + minLength + " characters long");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            return new Violation("password.needsLetterDigit", null,
                    "Password must contain at least one letter and one digit");
        }
        if (username != null) {
            String u = username.trim().toLowerCase(Locale.ROOT);
            if (u.length() >= USERNAME_MIN_FOR_CONTAINS && password.toLowerCase(Locale.ROOT).contains(u)) {
                return new Violation("password.containsUsername", null,
                        "Password must not contain the username");
            }
        }
        if (isCommon(password)) {
            return new Violation("password.common", null,
                    "Password is too common; choose a less predictable one");
        }
        return null;
    }

    /** Case-insensitive, and again with trailing digits removed. */
    static boolean isCommon(String password) {
        String p = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(p)) return true;
        String stripped = p.replaceFirst("\\d+$", "");
        return stripped.length() >= 4 && COMMON.contains(stripped);
    }

    private static Set<String> loadCommon() {
        Set<String> set = new HashSet<>();
        try (InputStream in = PasswordPolicy.class.getResourceAsStream("/common-passwords.txt")) {
            if (in == null) return Set.of();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String t = line.trim().toLowerCase(Locale.ROOT);
                    if (!t.isEmpty() && !t.startsWith("#")) set.add(t);
                }
            }
        } catch (IOException e) {
            return Set.of();
        }
        return Set.copyOf(set);
    }
}
