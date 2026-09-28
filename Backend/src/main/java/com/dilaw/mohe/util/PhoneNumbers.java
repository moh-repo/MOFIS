package com.dilaw.mohe.util;

import java.util.Objects;
import java.util.regex.Pattern;
import com.dilaw.mohe.exception.LocalizedArgumentException;
import com.dilaw.mohe.exception.LocalizedMessage;

/**
 * Phone-number rules for the student contact fields, the server half of the front end's
 * {@code phone-number.util.ts}.
 *
 * <p>A number is measured in digits, not characters. Separators (space, dash, dot,
 * brackets) and one leading {@code +} are allowed where a person would write them. At
 * most 15 digits (E.164, country code included); at least 8 for a landline or emergency
 * number (a Yemeni landline with its area code), at least 9 for a mobile (a Yemeni
 * mobile is 9 digits).
 *
 * <p>This is checked where a number comes in, not as a constraint on {@code StudentDatum}:
 * rows saved under the old character-count rule would otherwise fail validation on every
 * later save, including ones that never touch the phone.
 */
public final class PhoneNumbers {

    public static final int PHONE_MIN_DIGITS = 8;
    public static final int MOBILE_MIN_DIGITS = 9;
    public static final int PHONE_MAX_DIGITS = 15;
    /** Characters, separators included. */
    public static final int PHONE_MAX_LENGTH = 25;

    /** {@code \p{Nd}} so a number typed with Arabic-Indic digits is measured the same way. */
    private static final Pattern SHAPE = Pattern.compile("^\\+?[\\p{Nd}\\s\\-().]+$");

    private PhoneNumbers() {}

    /** True for an acceptable number. Blank is acceptable: the fields are optional. */
    public static boolean isValid(String value, boolean mobile) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String phone = value.strip();
        if (phone.length() > PHONE_MAX_LENGTH || !SHAPE.matcher(phone).matches()) {
            return false;
        }
        long digits = phone.codePoints().filter(Character::isDigit).count();
        return digits >= (mobile ? MOBILE_MIN_DIGITS : PHONE_MIN_DIGITS) && digits <= PHONE_MAX_DIGITS;
    }

    /** @throws IllegalArgumentException naming the field when the number is not acceptable */
    public static void require(String field, String value, boolean mobile) {
        if (!isValid(value, mobile)) {
            // The field by its name, in the reader's language (W3-08): the sentence
            // started with the Java field name and was English on every screen.
            throw new LocalizedArgumentException(field + ": a " + (mobile ? "mobile" : "phone")
                    + " number must have " + (mobile ? MOBILE_MIN_DIGITS : PHONE_MIN_DIGITS)
                    + " to " + PHONE_MAX_DIGITS + " digits.",
                    "validation.phone", LocalizedMessage.field(field),
                    mobile ? MOBILE_MIN_DIGITS : PHONE_MIN_DIGITS, PHONE_MAX_DIGITS);
        }
    }

    /**
     * A student's mobile number must be given (live QA L9, decision D11): when a student
     * is added, and when the student form saves an edit of one who has none yet. Other
     * paths (quick edit, approvals, imports, portal requests) do not require it, so an
     * old record without one can still be changed there.
     *
     * @throws LocalizedArgumentException (400) naming the field, in the reader's language
     */
    public static void requireMobilePresent(String value) {
        if (value == null || value.isBlank()) {
            throw new LocalizedArgumentException("studentCellphone: a mobile number is required.",
                    "validation.mobileRequired", LocalizedMessage.field("studentCellphone"));
        }
    }

    /**
     * Checks {@code after} only when it differs from {@code before}, so a stored number
     * nobody edited never blocks an unrelated change to the same student.
     */
    public static void requireIfChanged(String field, String before, String after, boolean mobile) {
        if (!Objects.equals(normalize(before), normalize(after))) {
            require(field, after, mobile);
        }
    }

    /** The three student contact numbers, keyed by their JSON field names. */
    public static boolean isContactField(String field) {
        return "studentCellphone".equals(field)
                || "studentHomeNumber".equals(field)
                || "studentEmergencyContact".equals(field);
    }

    /** Whether the contact field holds a mobile number (the stricter minimum). */
    public static boolean isMobileField(String field) {
        return "studentCellphone".equals(field);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
