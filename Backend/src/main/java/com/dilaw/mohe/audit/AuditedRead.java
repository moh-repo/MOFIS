package com.dilaw.mohe.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a handler whose <em>read</em> is worth an audit row (security plan O-33).
 *
 * <p>Writes have always been audited by the database triggers; reads of
 * personal data left no trace. A document download, a single-student detail
 * view or a bulk export is recorded by {@link AuditedReadAspect} in
 * {@code MOHECORE.REPORT_ACCESS_LOG} — who, which resource, which record or
 * filter, from which address, under which country — the same table the report
 * exports have used since V065, so one query answers "who looked at this
 * student".
 *
 * <p>Report exports rendered through {@code ReportExports} are logged there
 * without an annotation; this is for the handlers that stream a file or a
 * record directly.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditedRead {

    /** The resource code written to the row, e.g. {@code STUDENT_DOCUMENT}. */
    String resource();

    /** The format column: {@code DOWNLOAD}, {@code VIEW}, {@code PDF}, {@code EXCEL}. */
    String format() default "VIEW";

    /**
     * True for a single-record detail view. Decision D-7: exports and downloads
     * are always logged; detail views only when {@code mohe.audit.detail-views}
     * is on, because they are the highest-volume read in the system.
     */
    boolean detailView() default false;
}
