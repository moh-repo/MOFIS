package com.dilaw.mohe.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Every permission code the backend enforces, in one place.
 *
 * <p>Security plan R-11 (6 Sep 2026). Codes live in three places — the
 * {@code UMSCORE.Permissions} table, the SpEL constants in
 * {@link MoheSecurityExpressions} (and a handful of controller annotations),
 * and the Angular permission map — and until now nothing checked that they
 * agreed. They did not: {@code MOHE_QUARTER.*} was enforced by five endpoints
 * and existed in no database, so nobody but an administrator could ever hold
 * it, and {@code WAFAR.APPROVE} was accepted by an expression that no seed
 * script had ever inserted.
 *
 * <p>This registry is the source of truth for the <em>backend</em> side:
 * <ul>
 *   <li>{@code PermissionCatalogArchTest} fails the build if a
 *       {@code hasAuthority(…)} appears in {@code src/main} for an X not listed
 *       here;</li>
 *   <li>{@link PermissionCatalogStartupGuard} refuses to start (outside the dev
 *       profile) if the database lacks a code listed here;</li>
 *   <li>{@code V089__permission_catalog_seed.sql} inserts the codes that were
 *       missing on 6 Sep 2026, and is the pattern for future additions.</li>
 * </ul>
 *
 * <p>Codes that exist only in the database — page codes the frontend uses to
 * build menus ({@code REP_*}, {@code SYS_*}, {@code UMS_ROLES.*} …) — are not
 * listed: this registry does not claim to be the whole permission set, only the
 * part the server enforces. The synthetic authorities {@code ROLE_ADMIN} and
 * {@code ROLE_SUPER_ADMIN} are granted from account flags by
 * {@link JwtAuthFilter}, not held in the table, and are listed under
 * {@link #SYNTHETIC} so the architecture test can tell them apart.
 */
public final class PermissionCatalog {

    /** One enforced permission. Description is what the permission screen shows. */
    public record Entry(String code, String resource, String action, String description) {}

    /**
     * Authorities the JWT filter derives from the account's tier role (R-12),
     * never stored in UMSCORE.Permissions: one {@code ROLE_<tier>} per
     * {@link com.dilaw.mohe.ums.model.AdminTier}. {@code ROLE_ADMIN} is no
     * longer emitted; it stays listed so the architecture test recognises a
     * stray reference as synthetic rather than as an unknown code.
     */
    public static final Set<String> SYNTHETIC = Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN",
            "ROLE_MINISTRY_ADMIN", "ROLE_MINISTRY_STAFF", "ROLE_COUNTRY_ADMIN");

    /**
     * R-14: direct grants of these codes are configuration, not exceptions, and
     * need neither reason nor expiry. Today that is the per-user dashboard
     * sections of V071 ({@code DASHBOARD_<SECTION>.VIEW}), which every account
     * starts with and administrators revoke individually.
     */
    public static boolean isPerUserConfiguration(String code) {
        return code != null && code.startsWith("DASHBOARD_") && code.endsWith(".VIEW");
    }

    private static Entry e(String code, String description) {
        int dot = code.indexOf('.');
        return new Entry(code, code.substring(0, dot), code.substring(dot + 1), description);
    }

    public static final List<Entry> ENTRIES = List.of(
            // ── Students ───────────────────────────────────────────────────────
            e("STUDENTS.VIEW", "View student records (legacy page code)"),
            e("STUDENTS.ADD", "Add student records (legacy page code)"),
            e("STUDENTS.EDIT", "Edit student records (legacy page code)"),
            e("STUDENTS.DELETE", "Delete student records (legacy page code)"),
            e("STUDENTS_LIST.VIEW", "View the student list"),
            e("STUDENTS_LIST.SEARCH", "Search the student list"),
            e("STUDENTS_LIST.ADD", "Add a student from the list"),
            e("STUDENTS_LIST.EDIT", "Edit a student from the list"),
            e("STUDENTS_LIST.DELETE", "Delete a student from the list"),
            e("STUDENTS_LIST.EXPORT", "Export the student list"),
            e("STUDENTS_ADD.VIEW", "Open the add-student screen"),
            e("STUDENTS_ADD.ADD", "Create a student"),
            e("STUDENTS_ADD.EDIT", "Edit on the add-student screen"),
            e("STUDENTS_ADD.DELETE", "Delete on the add-student screen"),
            e("STUDENT_QUICK_EDIT.VIEW", "Quick edit: view, including identity fields"),
            e("STUDENT_HISTORY.VIEW", "View a student's history"),
            e("STUDENT_INFO_REQUESTS.VIEW", "View information requests to students"),
            e("STUDENT_INFO_REQUESTS.EDIT", "Raise, cancel and decide information requests to students"),
            e("STUDENT_TRAVEL.VIEW", "View travel tickets"),
            e("STUDENT_TRAVEL.ADD", "Add travel tickets"),
            e("STUDENT_TRAVEL.EDIT", "Edit travel tickets"),
            e("STUDENT_TRAVEL.DELETE", "Delete travel tickets"),
            e("STUDENT_TRAVEL.EXPORT", "Export travel tickets"),
            e("STUDENT_EXTENSION.VIEW", "View study extensions"),
            e("STUDENT_EXTENSION.ADD", "Add study extensions"),
            e("STUDENT_EXTENSION.EDIT", "Edit study extensions"),
            e("STUDENT_EXTENSION.DELETE", "Delete study extensions"),
            e("STUDENT_EXTENSION.APPROVE", "Approve study extensions"),
            e("STUDENT_EXTENSION.EXPORT", "Export study extensions"),
            // ── Finance ────────────────────────────────────────────────────────
            e("FINANCE.VIEW", "View student finance"),
            e("FINANCE.EDIT", "Edit student finance"),
            e("FINANCE.EXPORT", "Export student finance to Excel"),
            e("FINANCE_ARCH.VIEW", "View the finance archive"),
            e("FINANCE_ARCH.EDIT", "Edit the finance archive"),
            e("FINANCE_ARCH.DELETE", "Delete from the finance archive"),
            e("FINANCE_ARCH.EXPORT", "Export the finance archive"),
            e("PAYMENT_AUTH.VIEW", "View payment authorisations"),
            e("PAYMENT_AUTH.ADD", "Create payment authorisations"),
            e("PAYMENT_AUTH.EDIT", "Edit payment authorisations"),
            e("PAYMENT_AUTH.DELETE", "Delete payment authorisations"),
            e("PAYMENT_AUTH.APPROVE", "Release (approve) payment authorisations"),
            e("PAYMENT_AUTH.EXPORT", "Export payment authorisations"),
            e("WAFAR.VIEW", "View Wafar (attaché savings)"),
            e("WAFAR.ADD", "Record Wafar spending — attaché staff only"),
            e("WAFAR.EDIT", "Edit Wafar spending — attaché staff only"),
            e("WAFAR.DELETE", "Cancel Wafar spending — attaché staff only"),
            e("WAFAR.APPROVE", "Approve Wafar spending — attaché staff only (super admin audited)"),
            e("WAFAR.EXPORT", "Export Wafar"),
            e("MOHE_QUARTER.VIEW", "View financial quarters"),
            e("MOHE_QUARTER.ADD", "Open a financial quarter"),
            e("MOHE_QUARTER.EDIT", "Edit a financial quarter"),
            e("MOHE_QUARTER.CLOSE", "Close a financial quarter"),
            e("MOHE_QUARTER.DELETE", "Delete a financial quarter"),
            // ── Accounting ─────────────────────────────────────────────────────
            e("ACCOUNTING.VIEW", "View the accounting module"),
            e("ACCOUNTING.EDIT", "Edit accounting records"),
            e("COA.EDIT", "Maintain the chart of accounts"),
            e("BUDGET.EDIT", "Maintain budget appropriations"),
            e("ALLOCATION.CREATE", "Create allocations (maker)"),
            e("ALLOCATION.APPROVE", "Approve allocations (checker)"),
            e("PAYMENT_BATCH.CREATE", "Prepare payment batches (maker)"),
            e("PAYMENT_BATCH.APPROVE", "Approve payment batches (checker)"),
            e("FISCAL_PERIOD.CLOSE", "Close fiscal periods"),
            e("FISCAL_YEAR.ADD", "Open a new fiscal year"),
            // ── Approvals ──────────────────────────────────────────────────────
            e("APPROVALS.VIEW", "View the approvals module"),
            e("APP_CONFIG.EDIT", "Create, change and remove approval rules"),
            e("APP_PENDING.VIEW", "View pending approvals"),
            e("APP_PENDING.APPROVE", "Approve pending requests"),
            e("APP_PENDING.REJECT", "Reject pending requests"),
            // ── Reports and dashboards ─────────────────────────────────────────
            e("REPORTS.VIEW", "View reports"),
            e("REPORT_ACADEMIC.VIEW", "View academic reports"),
            e("REPORT_ACADEMIC.EXPORT", "Export academic reports"),
            e("REPORT_FINANCE.VIEW", "View finance reports"),
            e("REPORT_FINANCE.EXPORT", "Export finance reports"),
            e("REPORT_OTHER.VIEW", "View other reports"),
            e("REPORT_OTHER.EXPORT", "Export other reports"),
            e("DASHBOARD.VIEW", "View the dashboard"),
            e("DASHBOARD_STUDENTS.VIEW", "Dashboard: students"),
            e("DASHBOARD_FINANCE.VIEW", "Dashboard: finance"),
            e("DASHBOARD_APPROVALS.VIEW", "Dashboard: approvals"),
            e("DASHBOARD_ALERTS.VIEW", "Dashboard: alerts"),
            e("DASHBOARD_COUNTRIES.VIEW", "Dashboard: countries"),
            e("DASHBOARD_USERS.VIEW", "Dashboard: users"),
            e("DASHBOARD_QUARTER.VIEW", "Dashboard: quarter"),
            e("DASHBOARD_NOTICES.VIEW", "Dashboard: notices"),
            // ── Alerts and notifications ───────────────────────────────────────
            e("ALERT_EVENTS.VIEW", "View alert events"),
            e("ALERT_EVENTS.MANAGE", "Manage alert events"),
            e("ALERT_SCENARIOS.MANAGE", "Manage alert scenarios"),
            e("NOTIFICATIONS.VIEW", "View notifications"),
            e("NOTIFICATIONS.SEND", "Send notifications"),
            e("NOTIFICATIONS.SETTINGS", "Maintain notification settings"),
            // ── Archiving, audit, settings, system ─────────────────────────────
            e("ARCHIVING.VIEW", "View archived records"),
            e("ARCHIVING.EDIT", "Archive records"),
            e("ARCHIVING.DELETE", "Delete archived records"),
            e("ARCHIVING.RESTORE", "Restore archived records"),
            e("AUDIT.VIEW", "View the audit trail"),
            e("SETTINGS.VIEW", "View system settings"),
            e("SETTINGS.EDIT", "Edit system settings"),
            e("SYSTEM.RESTORE", "Restore a database backup"),
            // ── User management ────────────────────────────────────────────────
            e("UMS.VIEW", "View the user-management module"),
            e("UMS_USERS.VIEW", "View staff accounts"),
            e("UMS_ADMIN.VIEW", "View administration"),
            e("UMS_ADMIN.ADD", "Create staff accounts"),
            e("UMS_ADMIN.EDIT", "Edit staff accounts"),
            e("UMS_ADMIN.DELETE", "Delete staff accounts"),
            // ── Access review (S-04, V099) ─────────────────────────────────────
            e("ACCESS_REVIEW.VIEW", "See the quarterly access reviews the caller administers"),
            e("ACCESS_REVIEW.EDIT", "Attest each account of a review: keep, change or remove")
    );

    private static final Map<String, Entry> BY_CODE = ENTRIES.stream()
            .collect(Collectors.toUnmodifiableMap(Entry::code, x -> x));

    private PermissionCatalog() {
    }

    public static Set<String> codes() {
        return BY_CODE.keySet();
    }

    public static boolean contains(String code) {
        return BY_CODE.containsKey(code);
    }

    /** Codes grouped by resource, in resource order — the shape the permission screen wants. */
    public static Map<String, List<Entry>> byResource() {
        Map<String, List<Entry>> out = new TreeMap<>();
        for (Entry entry : ENTRIES) {
            out.computeIfAbsent(entry.resource(), k -> new java.util.ArrayList<>()).add(entry);
        }
        return out;
    }
}
