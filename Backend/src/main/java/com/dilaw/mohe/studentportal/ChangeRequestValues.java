package com.dilaw.mohe.studentportal;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How the text a student types into a change request is read for the student
 * field it targets (review R2b).
 *
 * <p>A request's value is kept as text until someone approves it. Approval used
 * to parse a date with {@code Instant.parse}, so "1-1-2000" was accepted at
 * submission and failed only when the ministry tried to approve it. The value is
 * now checked by the field's Java type when it comes in, and stored in one form:
 * a date as {@code yyyy-MM-dd}, a number as ASCII digits. Approval reads it the
 * same way, which also rescues requests stored before this rule.
 */
public final class ChangeRequestValues {

    /** The kinds a CHANGE_REQUEST_FIELD row can carry (V115). */
    public static final String TEXT = "TEXT";
    public static final String DATE = "DATE";
    public static final String NUMBER = "NUMBER";
    public static final String EMAIL = "EMAIL";

    /** A value its field cannot hold; {@link #kind()} says which rule it broke. */
    public static final class InvalidValue extends RuntimeException {
        private final String kind;

        InvalidValue(String kind, String value) {
            super(kind + ": " + value);
            this.kind = kind;
        }

        public String kind() {
            return kind;
        }
    }

    /** Year first: 2000-01-31, 2000/1/31, 2000.1.31. */
    private static final Pattern YMD = Pattern.compile("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})");
    /** Day first, as dates are written in Arabic: 31-1-2000, 31/01/2000. */
    private static final Pattern DMY = Pattern.compile("(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})");
    private static final Pattern EMAIL_SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private ChangeRequestValues() {}

    /**
     * The calendar date {@code raw} names, or null when it names none. Accepts
     * year-first and day-first dates with -, / or . between the parts, Arabic-Indic
     * digits, and the ISO instants older requests stored (read in {@code zone}).
     * Years outside 1900-2100 are refused: they are typing slips, not dates.
     */
    public static LocalDate parseDate(String raw, ZoneId zone) {
        if (raw == null) return null;
        String s = asciiDigits(raw.strip());
        if (s.isEmpty()) return null;
        if (s.indexOf('T') > 0) {
            try {
                return Instant.parse(s).atZone(zone).toLocalDate();
            } catch (DateTimeParseException ignored) {
                // not an instant; try a local date-time
            }
            try {
                return LocalDateTime.parse(s).toLocalDate();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        Matcher m = YMD.matcher(s);
        if (m.matches()) return date(m.group(1), m.group(2), m.group(3));
        m = DMY.matcher(s);
        if (m.matches()) return date(m.group(3), m.group(2), m.group(1));
        return null;
    }

    private static LocalDate date(String year, String month, String day) {
        try {
            LocalDate d = LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
            return d.getYear() >= 1900 && d.getYear() <= 2100 ? d : null;
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    /** Arabic-Indic (U+0660) and extended (U+06F0) digits as ASCII; everything else unchanged. */
    static String asciiDigits(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '٠' && c <= '٩') b.append((char) ('0' + (c - '٠')));
            else if (c >= '۰' && c <= '۹') b.append((char) ('0' + (c - '۰')));
            else b.append(c);
        }
        return b.toString();
    }

    /**
     * {@code raw} in the form a request keeps it for a field of {@code javaType}.
     * Blank stays blank; the caller decides whether that is allowed.
     *
     * @param dataType the field's CHANGE_REQUEST_FIELD.DATA_TYPE; only EMAIL adds a rule
     * @throws InvalidValue when the field cannot hold the value
     */
    public static String normalize(Class<?> javaType, String dataType, String raw, ZoneId zone) {
        if (raw == null || raw.isBlank()) return raw;
        String v = raw.strip();
        if (javaType == Instant.class || javaType == LocalDate.class) {
            LocalDate d = parseDate(v, zone);
            if (d == null) throw new InvalidValue(DATE, raw);
            return d.toString();
        }
        if (javaType == Integer.class || javaType == int.class) {
            String n = asciiDigits(v);
            try {
                return String.valueOf(Integer.parseInt(n));
            } catch (NumberFormatException e) {
                throw new InvalidValue(NUMBER, raw);
            }
        }
        if (EMAIL.equals(dataType) && !EMAIL_SHAPE.matcher(v).matches()) {
            throw new InvalidValue(EMAIL, raw);
        }
        return v;
    }

    /**
     * The stored text as a value of {@code javaType}: a date becomes the first
     * instant of that day in {@code zone}. Expects text {@link #normalize} accepts.
     */
    public static Object toFieldValue(Class<?> javaType, String value, ZoneId zone) {
        if (value == null || value.isBlank()) return javaType == String.class ? value : null;
        if (javaType == Instant.class) {
            LocalDate d = parseDate(value, zone);
            if (d == null) throw new InvalidValue(DATE, value);
            return d.atStartOfDay(zone).toInstant();
        }
        if (javaType == LocalDate.class) {
            LocalDate d = parseDate(value, zone);
            if (d == null) throw new InvalidValue(DATE, value);
            return d;
        }
        if (javaType == Integer.class || javaType == int.class) {
            try {
                return Integer.valueOf(asciiDigits(value.strip()));
            } catch (NumberFormatException e) {
                throw new InvalidValue(NUMBER, value);
            }
        }
        return value;
    }

    /** How a stored field value is shown in a request: a date as yyyy-MM-dd in {@code zone}. */
    public static String display(Object value, ZoneId zone) {
        if (value == null) return null;
        if (value instanceof Instant i) return i.atZone(zone).toLocalDate().toString();
        return value.toString();
    }
}
