package com.dilaw.mohe.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field-level access, read and write, resolved through the UMS field catalogue
 * (security plan R-18, generalising {@link SensitiveField}).
 *
 * <p>A field names the UMS page and field it corresponds to. When the
 * administrators have generated permissions for that field (the Fields screen
 * creates {@code <page>.<field>.VIEW} and {@code .EDIT}), those decide: a
 * caller without {@code .VIEW} receives {@code null} for the field in every
 * JSON response, and one without {@code .EDIT} who submits a change to it is
 * refused with 400 (decision D-9 a: reject, do not silently ignore). Until the
 * field permissions exist, {@link #anyOf} and {@link #editAnyOf} apply as the
 * fallback, exactly as {@code @SensitiveField} always has.
 *
 * <pre>
 *   {@literal @}FieldPermission(page = "STUDENTS_LIST", field = "STUDENT_EMAIL",
 *                    anyOf = {"STUDENT_QUICK_EDIT.VIEW", "UMS_ADMIN.VIEW"})
 *   private String studentEmail;
 * </pre>
 *
 * <p>Enforced on the read side by {@link SensitiveFieldModule} in the global
 * {@code ObjectMapper}; on the write side by
 * {@code FieldAccessService.assertMayEdit}, called by the handlers that accept
 * the DTO.
 *
 * <p>Reports and exports do not pass through Jackson and are masked separately
 * by {@code security/field/ReportFieldMask} (W5-03), which resolves a report
 * row's property to the rule declared here — by the same name, or through
 * {@link MaskedAs} where the row renames it. See {@code ACCESS_CONTROL.md}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FieldPermission {

    /** UMS page code (a {@code UMSCORE.Resources} row of type PAGE). Empty: fallback rules only. */
    String page() default "";

    /** UMS field code under that page. Empty: fallback rules only. */
    String field() default "";

    /** Read fallback: the caller must hold one of these to see the value. Empty: visible. */
    String[] anyOf() default {};

    /** Write fallback: the caller must hold one of these to change the value. Empty: writable. */
    String[] editAnyOf() default {};

    /** The row's own user always sees it (an account reading its own e-mail). */
    boolean ownerVisible() default false;
}
