package com.dilaw.mohe.codes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ties a class of {@code public static final String} constants (or an enum) to
 * the code list its values must exist in.
 *
 * <p>The rule since V072: the database owns the catalogue — which codes exist,
 * their names, order, colour and icon — and Java keeps a constant only for a
 * code its behaviour depends on. {@code CodeListStartupGuard} proves the two
 * agree at boot: with {@link Mode#EXACT} the constants must equal the list's
 * {@code IS_SYSTEM = 1} rows; with {@link Mode#SUBSET} each constant must be an
 * active row (for lists that also carry many non-system codes, such as the
 * report labels).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CodeList {

    /** The list name as registered in {@code CodeListRegistry} (e.g. {@code alert-severity}). */
    String value();

    Mode mode() default Mode.EXACT;

    enum Mode { EXACT, SUBSET }
}
