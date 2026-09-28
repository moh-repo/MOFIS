package com.dilaw.mohe.calendar.service;

import java.util.List;

/**
 * The kinds of thing the day agenda can list, and the authorities each needs.
 *
 * <p>These are not a code list and deliberately have no {@code LU_} table: a kind
 * is a branch in {@link CalendarAgendaService} paired with a screen in the
 * browser, the way {@code DashboardSection} is. Adding one is a code change on
 * both sides, so there is nothing an administrator could usefully edit. The
 * domain codes the entries <em>carry</em> — severities, statuses, channels — come
 * from the code lists as usual.
 *
 * <p>Each kind lists the authorities that open the screen it links to, copied
 * from the expression guarding that screen's controller in
 * {@code MoheSecurityExpressions}, so the agenda never offers a row the caller
 * cannot follow. {@code ROLE_ADMIN} — which {@code JwtAuthFilter} grants to
 * administrators and which passes every one of those expressions — is handled
 * once in the service rather than repeated here.
 */
public final class CalendarEntryKind {

    private CalendarEntryKind() {
    }

    /** A student document whose expiry date is that day — the only forward-looking kind. */
    public static final String DOCUMENT_EXPIRY = "DOCUMENT_EXPIRY";

    /** An alert-event case opened that day. */
    public static final String ALERT_CASE = "ALERT_CASE";

    /** A notification batch sent that day. */
    public static final String NOTIFICATION = "NOTIFICATION";

    /** A quarter's payment date. */
    public static final String QUARTER_PAY = "QUARTER_PAY";

    /** A quarter's received date. */
    public static final String QUARTER_RECEIVED = "QUARTER_RECEIVED";

    /** A scheduled report due to run, or last run, that day. */
    public static final String REPORT_RUN = "REPORT_RUN";

    /** An approval request raised that day and still waiting for a decision (review round 2, H1). */
    public static final String APPROVAL_PENDING = "APPROVAL_PENDING";

    /** Every kind, in the order the agenda lists them. */
    public static final List<String> ALL = List.of(
            APPROVAL_PENDING, DOCUMENT_EXPIRY, ALERT_CASE, QUARTER_PAY, QUARTER_RECEIVED, REPORT_RUN, NOTIFICATION);

    /** Any one of these authorities admits {@code kind}; an unknown kind admits nobody. */
    static List<String> authoritiesFor(String kind) {
        return switch (kind) {
            // MoheSecurityExpressions.STUDENT_PII, read side.
            case DOCUMENT_EXPIRY -> List.of(
                    "STUDENTS.VIEW", "STUDENTS.EDIT", "STUDENTS.ADD",
                    "STUDENTS_LIST.VIEW", "STUDENTS_LIST.EDIT", "STUDENTS_ADD.VIEW",
                    "STUDENT_QUICK_EDIT.VIEW", "STUDENT_HISTORY.VIEW", "UMS_ADMIN.VIEW");
            // MoheSecurityExpressions.ALERT_EVENTS_VIEW.
            case ALERT_CASE -> List.of("ALERT_EVENTS.VIEW", "ALERT_EVENTS.MANAGE", "UMS_ADMIN.VIEW");
            // NotificationController.getHistory. NOTIFICATIONS.SEND is not enough:
            // it sends messages, it does not open the history.
            case NOTIFICATION -> List.of("NOTIFICATIONS.VIEW");
            // MoheSecurityExpressions.MOHE_QUARTER.
            case QUARTER_PAY, QUARTER_RECEIVED -> List.of(
                    "MOHE_QUARTER.VIEW", "MOHE_QUARTER.ADD", "MOHE_QUARTER.EDIT",
                    "MOHE_QUARTER.DELETE", "MOHE_QUARTER.CLOSE", "SETTINGS.VIEW", "UMS_ADMIN.VIEW");
            // MoheSecurityExpressions.REPORT_OTHER — the hub /reports/custom sits under.
            case REPORT_RUN -> List.of(
                    "REPORT_OTHER.VIEW", "REPORT_OTHER.EXPORT", "REPORTS.VIEW", "UMS_ADMIN.VIEW");
            // MoheSecurityExpressions.APPROVALS.
            case APPROVAL_PENDING -> List.of(
                    "APPROVALS.VIEW", "APP_PENDING.VIEW", "APP_PENDING.APPROVE", "APP_PENDING.REJECT",
                    "UMS_ADMIN.VIEW");
            default -> List.of();
        };
    }
}
