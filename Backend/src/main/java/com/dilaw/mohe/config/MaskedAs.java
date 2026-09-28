package com.dilaw.mohe.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A report or export property that carries a masked entity field under another
 * name (plan W5-03 / R-18 / O-03).
 *
 * <p>Report rows are masked by {@code security/field/ReportFieldMask}, which
 * matches a row property to the {@link FieldPermission} / {@link SensitiveField}
 * of the same name on one of the rule-owning entities. A row that renames the
 * datum — {@code passportNumber} for {@code studentPassportNumber},
 * {@code nationalNumber} for the national id — says so here, and is masked by
 * the entity field's rule, so the rule keeps one home:
 *
 * <pre>
 *   {@literal @}MaskedAs(type = StudentDatum.class, field = "studentPassportNumber")
 *   private String passportNumber;
 * </pre>
 *
 * <p>Read only by the report and export masks. The JSON writer still reads
 * {@link FieldPermission} and {@link SensitiveField} alone: a row type that
 * needs masking in JSON too carries the rule itself.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MaskedAs {

    /** The entity whose annotated field holds the rule. */
    Class<?> type();

    /** That field's property name. */
    String field();
}
