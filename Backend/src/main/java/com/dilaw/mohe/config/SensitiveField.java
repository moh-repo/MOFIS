package com.dilaw.mohe.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a JPA entity field as sensitive. During JSON serialization the field value is
 * replaced with {@code null} unless the authenticated user holds at least one of the
 * listed Spring Security authority strings.
 *
 * <p>Enforced transparently by {@link SensitiveFieldModule} registered in the global
 * Jackson {@code ObjectMapper} — no controller changes required.
 *
 * <p>Example:
 * <pre>
 *   {@literal @}SensitiveField(anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW"})
 *   private String studentPassportNumber;
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SensitiveField {
    /** Authority codes: the caller must hold at least one for the field to be serialized. */
    String[] anyOf();
}
