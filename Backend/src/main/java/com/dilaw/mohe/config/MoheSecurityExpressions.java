package com.dilaw.mohe.config;

/**
 * Shared {@link org.springframework.security.access.prepost.PreAuthorize} SpEL fragments.
 * <p>
 * Authorities are loaded from UMS ({@code UmsPermission.permissionCode}) per request; codes must match exactly.
 * Ensure these exist in UMSCORE for affected users/roles. Since the R-12 pass of the security plan (6 Sep 2026)
 * no expression accepts {@code ROLE_ADMIN}: an administrator passes a gate by holding the code, through the
 * permission bundle of their tier role ({@code SUPER_ADMIN}, {@code MINISTRY_ADMIN}, {@code MINISTRY_STAFF},
 * {@code COUNTRY_ADMIN}; see {@code AdminTier} and V094). Only {@link #SYSTEM_RESTORE} and
 * {@link #WAFAR_APPROVE} name a tier ({@code ROLE_SUPER_ADMIN}), because only the super admin may do those
 * regardless of codes. Since the 27 Aug 2026 revision no <em>view</em> permission stands in for an administrator:
 * {@code UMS.VIEW} is gone from every expression and {@code UMS_ADMIN.VIEW} from every write-side one, so the
 * accounting separation of duties (create vs approve) cannot be satisfied by a page-view grant. Codes:
 * {@code STUDENTS.VIEW}, {@code STUDENTS.EDIT}, {@code STUDENTS.ADD}, {@code STUDENT_QUICK_EDIT.VIEW},
 * {@code STUDENT_HISTORY.VIEW}, {@code STUDENT_INFO_REQUESTS.VIEW}, {@code SETTINGS.VIEW}, {@code APPROVALS.VIEW},
 * {@code AUDIT.VIEW}, {@code FINANCE.VIEW}, {@code FINANCE.EDIT}, {@code REPORTS.VIEW},
 * {@code WAFAR.*}, {@code STUDENT_TRAVEL.*}, {@code STUDENT_EXTENSION.*}, {@code FINANCE_ARCH.*},
 * {@code ARCHIVING.*}, {@code PAYMENT_AUTH.*}, {@code REPORT_ACADEMIC.*}, {@code REPORT_FINANCE.*}, {@code REPORT_OTHER.*}.
 * <p>
 * User management endpoints also accept {@code UMS_USERS.VIEW} (see {@code UmsUserController}).
 * New permission codes are seeded by UMSCORE_expand_permissions_v2.sql.
 */
public final class MoheSecurityExpressions {

    private MoheSecurityExpressions() {
    }

    /** UMS pages may use legacy {@code STUDENTS.*} or split {@code STUDENTS_LIST.*} / {@code STUDENTS_ADD.*}. */
    public static final String STUDENT_PII =
            "hasAuthority('STUDENTS.VIEW') "
                    + "or hasAuthority('STUDENTS.EDIT') "
                    + "or hasAuthority('STUDENTS.ADD') "
                    + "or hasAuthority('STUDENTS_LIST.VIEW') "
                    + "or hasAuthority('STUDENTS_LIST.EDIT') "
                    + "or hasAuthority('STUDENTS_LIST.ADD') "
                    + "or hasAuthority('STUDENTS_LIST.SEARCH') "
                    + "or hasAuthority('STUDENTS_LIST.EXPORT') "
                    + "or hasAuthority('STUDENTS_LIST.DELETE') "
                    + "or hasAuthority('STUDENTS_ADD.VIEW') "
                    + "or hasAuthority('STUDENTS_ADD.EDIT') "
                    + "or hasAuthority('STUDENTS_ADD.ADD') "
                    + "or hasAuthority('STUDENTS_ADD.DELETE') "
                    + "or hasAuthority('STUDENT_QUICK_EDIT.VIEW') "
                    + "or hasAuthority('STUDENT_HISTORY.VIEW') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /**
     * Naming a student in a dropdown, on a screen that is not a student screen.
     *
     * <p>The finance recipients form has to say which student a payment was
     * collected for, but the three roles that run the finance screens — Finance
     * Manager, FINANCE_OFFICER, WAFAR_OFFICER — hold no {@code STUDENTS.*}
     * permission at all, so the student list answered 403 and the dropdown was
     * empty. This gate exists for one endpoint, {@code /studentdata/options},
     * which returns an id and a name and nothing else; the student records
     * themselves stay behind {@link #STUDENT_PII}.
     */
    public static final String STUDENT_PICKER =
            "hasAuthority('FINANCE.VIEW') "
                    + "or hasAuthority('FINANCE.EDIT') "
                    + "or " + STUDENT_PII;

    /** Student record writes: the ADD/EDIT side of {@link #STUDENT_PII}, without the VIEW-only codes. */
    public static final String STUDENT_WRITE =
            "hasAuthority('STUDENTS.EDIT') "
                    + "or hasAuthority('STUDENTS.ADD') "
                    + "or hasAuthority('STUDENTS_LIST.EDIT') "
                    + "or hasAuthority('STUDENTS_LIST.ADD') "
                    + "or hasAuthority('STUDENTS_LIST.DELETE') "
                    + "or hasAuthority('STUDENTS_ADD.EDIT') "
                    + "or hasAuthority('STUDENTS_ADD.ADD') "
                    + "or hasAuthority('STUDENTS_ADD.DELETE') ";

    /** Deleting a student record: the DELETE codes only. */
    public static final String STUDENT_DELETE =
            "hasAuthority('STUDENTS.DELETE') "
                    + "or hasAuthority('STUDENTS_LIST.DELETE') "
                    + "or hasAuthority('STUDENTS_ADD.DELETE') ";

    public static final String STUDENT_INFO_REQUESTS =
            "hasAuthority('STUDENT_INFO_REQUESTS.VIEW') "
                    + "or hasAuthority('STUDENTS.VIEW') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /**
     * Raising, withdrawing and deciding information requests (live QA H3, 25 Sep
     * 2026). {@link #STUDENT_INFO_REQUESTS} reads; until then it was also the only
     * gate on every write, so an account holding STUDENTS.VIEW alone raised
     * requests to students.
     */
    public static final String STUDENT_INFO_REQUESTS_WRITE =
            "hasAuthority('STUDENT_INFO_REQUESTS.EDIT') ";

    /**
     * System administration pages.
     *
     * <p>{@code SETTINGS.VIEW} reads; writing needs {@code SETTINGS.EDIT}. Until
     * August 2026 this was "VIEW or ..." for every verb, so a read-only settings
     * role could restore a backup, rotate the file-encryption key and run the
     * query builder. SpEL binds {@code and} tighter than {@code or}, so the first
     * clause is "a GET with VIEW"; the rest is the write side.
     */
    public static final String SYSTEM_ADMIN =
            "(@httpMethodSecurity.isGetRequest() and hasAuthority('SETTINGS.VIEW')) "
                    + "or hasAuthority('SETTINGS.EDIT') ";

    /** Settings writes regardless of verb, for the few POSTs that are reads in disguise to opt out of. */
    public static final String SETTINGS_WRITE =
            "hasAuthority('SETTINGS.EDIT') ";

    /**
     * S-06: per-country configuration (bank branches, and the like) is written
     * by the attaché's administrator as well as by whoever holds the settings
     * right; {@code CountryOwnedConfigPolicy} then decides which rows.
     */
    public static final String COUNTRY_CONFIG_WRITE =
            "hasAuthority('SETTINGS.EDIT') or hasRole('COUNTRY_ADMIN')";

    /** Lookup tables: GET is public reference data; mutations require system admin. */
    public static final String LOOKUP_TABLE =
            "@httpMethodSecurity.isGetRequest() or " + SYSTEM_ADMIN;

    public static final String APPROVALS =
            "hasAuthority('APPROVALS.VIEW') "
                    + "or hasAuthority('APP_PENDING.VIEW') "
                    + "or hasAuthority('APP_PENDING.APPROVE') "
                    + "or hasAuthority('APP_PENDING.REJECT') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /**
     * Approval rules: creating, changing and removing configurations and their
     * levels, which carry the role each step needs (FIX-PLAN ESC, 25 Sep 2026).
     * They sat under {@link #APPROVALS}, so an account that could only view
     * approvals could rewrite who approves what.
     */
    public static final String APPROVAL_CONFIG_WRITE =
            "hasAuthority('APP_CONFIG.EDIT') ";

    public static final String AUDIT =
            "hasAuthority('AUDIT.VIEW') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /** Finance reads. {@code FINANCE.EXPORT} admits them too: the Excel export reads the list (review F1). */
    public static final String FINANCE =
            "hasAuthority('FINANCE.VIEW') "
                    + "or hasAuthority('FINANCE.EDIT') "
                    + "or hasAuthority('FINANCE.EXPORT') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /**
     * Finance writes: create, update, delete, status and Wafar changes.
     *
     * <p>{@link #FINANCE} is the class-level gate on the finance controller and is
     * "VIEW or EDIT" — right for reads, and until August 2026 the only gate on
     * the writes too. Seven roles hold {@code FINANCE.VIEW}; four hold
     * {@code FINANCE.EDIT}. The other three could post and delete finance rows
     * through the API while the UI merely hid the buttons. A method-level
     * annotation replaces the class-level one, so this must stand on its own.
     */
    public static final String FINANCE_WRITE =
            "hasAuthority('FINANCE.EDIT') ";

    public static final String REPORTS_OR_FINANCE =
            "hasAuthority('REPORTS.VIEW') "
                    + "or hasAuthority('FINANCE.VIEW') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /** Wafar (savings) financial records — seeded by UMSCORE_expand_permissions_v2.sql. */
    public static final String WAFAR =
            "hasAuthority('WAFAR.VIEW') "
                    + "or hasAuthority('WAFAR.ADD') "
                    + "or hasAuthority('WAFAR.EDIT') "
                    + "or hasAuthority('WAFAR.DELETE') "
                    + "or hasAuthority('WAFAR.EXPORT') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /* ------------------------------------------------------------------ *
     * Write-side expressions added in the third revision pass (27 Aug 2026).
     *
     * The class-level constants above are "VIEW or ADD or EDIT ..." — right
     * for a read, wrong for a write. A5 in the 27 Aug plan split FINANCE,
     * STUDENTS and SETTINGS; the same "VIEW-only role can post" gap stood on
     * Wafar (approve/reject/delete), payment authorisation (bulk-authorize),
     * travel tickets and extensions. Each pair below is the ADD/EDIT/DELETE
     * side of the matching class-level constant, without the VIEW/EXPORT
     * codes and without UMS_ADMIN.VIEW, so a page-view grant can never write.
     * ------------------------------------------------------------------ */

    /**
     * Wafar spending: create, update, cancel/delete.
     *
     * <p>No {@code ROLE_ADMIN} here, unlike every other write expression (security
     * plan N-04, 6 Sep 2026). Wafar is money already transferred to one attaché, and
     * the rule is that the attaché alone records, edits and approves it: the
     * ministry reads, reports and reconciles the statements, but does not write the
     * rows. The country admins ({@code jo_admin}, {@code sa_admin}) hold the
     * {@code WAFAR.*} codes through the {@code Admin} role, so they lose nothing;
     * the accounts that lose the wildcard are the super admin, ministry users and
     * the all-country {@code admin}, which is the point. The service refuses those
     * accounts as well ({@code CountryScopeService.assertCountryOwnedWrite}), so a
     * ministry account that happens to hold {@code WAFAR.EDIT} is still refused.
     */
    public static final String WAFAR_WRITE =
            "hasAuthority('WAFAR.ADD') "
                    + "or hasAuthority('WAFAR.EDIT') "
                    + "or hasAuthority('WAFAR.DELETE')";

    /**
     * Wafar spending: approve or reject.
     *
     * <p>There is no {@code WAFAR.APPROVE} code in UMSCORE; approval has always
     * been an EDIT-holder's act. Kept as its own constant so a dedicated code can
     * be introduced without touching the controller, and because
     * {@code WafarSpendingService.approve} refuses the creator regardless.
     *
     * <p>{@code ROLE_ADMIN} removed with {@link #WAFAR_WRITE}. The one ministry
     * account still allowed to approve is the super admin — decision D-13(b),
     * for attachés where one officer is the whole finance desk — and that is
     * granted in the service by flag and audited, not here by authority.
     */
    /**
     * The super admin alone, by tier (review M2). The system settings (name, logo)
     * were writable by any SETTINGS.EDIT holder; they are the installation's, not a
     * settings officer's.
     */
    public static final String SUPER_ADMIN = "hasAuthority('ROLE_SUPER_ADMIN')";

    public static final String WAFAR_APPROVE =
            "hasAuthority('WAFAR.EDIT') "
                    + "or hasAuthority('WAFAR.APPROVE') "
                    + "or hasAuthority('ROLE_SUPER_ADMIN')";

    /** Payment authorisation records: create, update, delete. */
    public static final String PAYMENT_AUTH_WRITE =
            "hasAuthority('PAYMENT_AUTH.ADD') "
                    + "or hasAuthority('PAYMENT_AUTH.EDIT') "
                    + "or hasAuthority('PAYMENT_AUTH.DELETE') "
                    + "or hasAuthority('FINANCE.EDIT') ";

    /** Release payments ({@code POST /bulk-authorize}): the APPROVE code only. */
    public static final String PAYMENT_AUTH_APPROVE =
            "hasAuthority('PAYMENT_AUTH.APPROVE') ";

    /** Travel tickets: create, update, delete. */
    public static final String STUDENT_TRAVEL_WRITE =
            "hasAuthority('STUDENT_TRAVEL.ADD') "
                    + "or hasAuthority('STUDENT_TRAVEL.EDIT') "
                    + "or hasAuthority('STUDENT_TRAVEL.DELETE') ";

    /** Scholarship extensions: create, update, delete, approve. */
    public static final String STUDENT_EXTENSION_WRITE =
            "hasAuthority('STUDENT_EXTENSION.ADD') "
                    + "or hasAuthority('STUDENT_EXTENSION.EDIT') "
                    + "or hasAuthority('STUDENT_EXTENSION.DELETE') "
                    + "or hasAuthority('STUDENT_EXTENSION.APPROVE') ";

    /**
     * Restore the database from an uploaded backup.
     *
     * <p>Its own code ({@code SYSTEM.RESTORE}, created by V065 and granted to
     * nobody): a restore replaces every row in the system, which is not the
     * same act as editing a setting. Until the third pass this sat under
     * {@link #SYSTEM_ADMIN}, i.e. {@code SETTINGS.EDIT}.
     */
    public static final String SYSTEM_RESTORE =
            "hasAuthority('SYSTEM.RESTORE') ";

    /**
     * The whole landing page ({@code /}).
     *
     * <p>Since V070 (29 Aug 2026) the dashboard is governed by {@code DASHBOARD.*}
     * alone: {@code DASHBOARD.VIEW} opens every section, and a
     * {@code DASHBOARD_<SECTION>.VIEW} code opens one section (the constants
     * below). The report and UMS codes that used to open it as a side effect no
     * longer do — V070 granted {@code DASHBOARD.VIEW} to every role and user that
     * held one of them, so nobody lost the page on upgrade. The four legacy
     * handlers the reports pages still call keep the wider gate,
     * {@link #DASHBOARD_OR_REPORT}.
     */
    public static final String DASHBOARD_VIEW =
            "hasAuthority('DASHBOARD.VIEW') ";

    /**
     * The pre-V070 dashboard gate, kept only for {@code /charts/dashboard/overview}
     * and the three {@code /statistics} handlers that the comprehensive-dashboards
     * report still reads. The landing page itself no longer calls them.
     */
    public static final String DASHBOARD_OR_REPORT =
            "hasAuthority('DASHBOARD.VIEW') "
                    + "or hasAuthority('REPORT_OTHER.VIEW') "
                    + "or hasAuthority('REPORTS.VIEW') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /*
     * One gate per dashboard section (V070). Each is "the whole dashboard, or this
     * section". The section list itself lives in DashboardSection; a section
     * added there without a constant here fails DashboardScopeTest. Keep every
     * literal on its own hasAuthority('…') line: the frontend's
     * gen-security-codes.mjs scrapes this file for the codes its route guards may
     * use.
     */

    /** Student counts, intake by month, students by delegation year and by status. */
    public static final String DASHBOARD_STUDENTS =
            "hasAuthority('DASHBOARD.VIEW') "
                    + "or hasAuthority('DASHBOARD_STUDENTS.VIEW') ";

    /** Disbursed totals, disbursement by year and the Wafar balance. */
    public static final String DASHBOARD_FINANCE =
            "hasAuthority('DASHBOARD.VIEW') "
                    + "or hasAuthority('DASHBOARD_FINANCE.VIEW') ";

    /** Pending approvals, by entity type, and the caller's own pending requests. */
    public static final String DASHBOARD_APPROVALS =
            "hasAuthority('DASHBOARD.VIEW') "
                    + "or hasAuthority('DASHBOARD_APPROVALS.VIEW') ";

    /** Open student alert cases by status and severity. */
    public static final String DASHBOARD_ALERTS =
            "hasAuthority('DASHBOARD.VIEW') "
                    + "or hasAuthority('DASHBOARD_ALERTS.VIEW') ";

    /** Students by study country. */
    public static final String DASHBOARD_COUNTRIES =
            "hasAuthority('DASHBOARD.VIEW') "
                    + "or hasAuthority('DASHBOARD_COUNTRIES.VIEW') ";

    /** Staff accounts of the caller's countries (ministry: all accounts). */
    public static final String DASHBOARD_USERS =
            "hasAuthority('DASHBOARD.VIEW') "
                    + "or hasAuthority('DASHBOARD_USERS.VIEW') ";

    /** The open MOHE quarter visible to the caller. */
    public static final String DASHBOARD_QUARTER =
            "hasAuthority('DASHBOARD.VIEW') "
                    + "or hasAuthority('DASHBOARD_QUARTER.VIEW') ";

    /** System notices (MOHE_ALERTS) — not country-specific, and labelled so. */
    public static final String DASHBOARD_NOTICES =
            "hasAuthority('DASHBOARD.VIEW') "
                    + "or hasAuthority('DASHBOARD_NOTICES.VIEW') ";

    /** Student travel ticket management. */
    public static final String STUDENT_TRAVEL =
            "hasAuthority('STUDENT_TRAVEL.VIEW') "
                    + "or hasAuthority('STUDENT_TRAVEL.ADD') "
                    + "or hasAuthority('STUDENT_TRAVEL.EDIT') "
                    + "or hasAuthority('STUDENT_TRAVEL.DELETE') "
                    + "or hasAuthority('STUDENT_TRAVEL.EXPORT') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /** Student scholarship extension management. */
    public static final String STUDENT_EXTENSION =
            "hasAuthority('STUDENT_EXTENSION.VIEW') "
                    + "or hasAuthority('STUDENT_EXTENSION.ADD') "
                    + "or hasAuthority('STUDENT_EXTENSION.EDIT') "
                    + "or hasAuthority('STUDENT_EXTENSION.DELETE') "
                    + "or hasAuthority('STUDENT_EXTENSION.EXPORT') "
                    + "or hasAuthority('STUDENT_EXTENSION.APPROVE') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /** Archived university fee records — also accepts FINANCE.* for backward compatibility. */
    public static final String FINANCE_ARCH =
            "hasAuthority('FINANCE_ARCH.VIEW') "
                    + "or hasAuthority('FINANCE_ARCH.EDIT') "
                    + "or hasAuthority('FINANCE_ARCH.EXPORT') "
                    + "or hasAuthority('FINANCE_ARCH.DELETE') "
                    + "or hasAuthority('FINANCE.VIEW') "
                    + "or hasAuthority('FINANCE.EDIT') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /**
     * Correcting the finance columns of an archived quarter from the finance history
     * page (review F5). That page is opened with FINANCE_ARCH, not ARCHIVING, so the
     * archive controller's class-level gate never admitted its own editors.
     */
    public static final String FINANCE_ARCH_WRITE =
            "hasAuthority('FINANCE_ARCH.EDIT') "
                    + "or hasAuthority('ARCHIVING.EDIT') ";

    /**
     * Reading the quarterly student snapshots (STUDENT_DATA_ARCH). Two pages read
     * them — Student History (STUDENT_HISTORY) and the finance history
     * (FINANCE_ARCH) — besides archiving itself; gated on ARCHIVING alone, both
     * opened empty for their own roles (QA B7, STD-004).
     */
    public static final String STUDENT_ARCHIVE_READ =
            "hasAuthority('ARCHIVING.VIEW') "
                    + "or hasAuthority('ARCHIVING.EDIT') "
                    + "or hasAuthority('ARCHIVING.RESTORE') "
                    + "or hasAuthority('ARCHIVING.DELETE') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') "
                    + "or hasAuthority('STUDENT_HISTORY.VIEW') "
                    + "or hasAuthority('FINANCE_ARCH.VIEW') "
                    + "or hasAuthority('FINANCE_ARCH.EDIT') "
                    + "or hasAuthority('FINANCE_ARCH.EXPORT') "
                    + "or hasAuthority('FINANCE_ARCH.DELETE') "
                    + "or hasAuthority('FINANCE.VIEW') "
                    + "or hasAuthority('FINANCE.EDIT') ";

    /*
     * There is no STUDENT_ARCHIVE_WRITE or STUDENT_ARCHIVE_DELETE any more. The
     * archive is a record of what was on file in a quarter, written by the quarter
     * close and read-only afterwards (DEC-08 a); the generic create, overwrite and
     * delete that those expressions gated were reachable through the API by any
     * archiving administrator although no screen offered them (live QA H1, 23 Sep
     * 2026). The only write left is the finance-column correction, FINANCE_ARCH_WRITE.
     */

    /** Student data archiving and restoration. */
    public static final String ARCHIVING =
            "hasAuthority('ARCHIVING.VIEW') "
                    + "or hasAuthority('ARCHIVING.EDIT') "
                    + "or hasAuthority('ARCHIVING.RESTORE') "
                    + "or hasAuthority('ARCHIVING.DELETE') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /** Payment authorization records — also accepts FINANCE.* for existing finance roles. */
    public static final String PAYMENT_AUTH =
            "hasAuthority('PAYMENT_AUTH.VIEW') "
                    + "or hasAuthority('PAYMENT_AUTH.ADD') "
                    + "or hasAuthority('PAYMENT_AUTH.EDIT') "
                    + "or hasAuthority('PAYMENT_AUTH.APPROVE') "
                    + "or hasAuthority('PAYMENT_AUTH.EXPORT') "
                    + "or hasAuthority('PAYMENT_AUTH.DELETE') "
                    + "or hasAuthority('FINANCE.VIEW') "
                    + "or hasAuthority('FINANCE.EDIT') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /** Academic performance, enrollment, and extension statistics reports. */
    public static final String REPORT_ACADEMIC =
            "hasAuthority('REPORT_ACADEMIC.VIEW') "
                    + "or hasAuthority('REPORT_ACADEMIC.EXPORT') "
                    + "or hasAuthority('REPORTS.VIEW') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /** Financial, fiscal year, and fee structure reports — also accepts FINANCE.VIEW. */
    public static final String REPORT_FINANCE =
            "hasAuthority('REPORT_FINANCE.VIEW') "
                    + "or hasAuthority('REPORT_FINANCE.EXPORT') "
                    + "or hasAuthority('REPORTS.VIEW') "
                    + "or hasAuthority('FINANCE.VIEW') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /** Analytics, visualizations, chart data, and other reports. */
    public static final String REPORT_OTHER =
            "hasAuthority('REPORT_OTHER.VIEW') "
                    + "or hasAuthority('REPORT_OTHER.EXPORT') "
                    + "or hasAuthority('REPORTS.VIEW') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /** Alert event cases — view and filter. */
    public static final String ALERT_EVENTS_VIEW =
            "hasAuthority('ALERT_EVENTS.VIEW') "
                    + "or hasAuthority('ALERT_EVENTS.MANAGE') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /** Alert event cases — acknowledge, resolve, dismiss. */
    public static final String ALERT_EVENTS_MANAGE =
            "hasAuthority('ALERT_EVENTS.MANAGE') ";

    /** Alert scenario configuration — enable/disable, threshold. */
    public static final String ALERT_SCENARIOS_MANAGE =
            "hasAuthority('ALERT_SCENARIOS.MANAGE') ";

    /** MOHE quarter management — any quarter permission grants class-level access. */
    public static final String MOHE_QUARTER =
            "hasAuthority('MOHE_QUARTER.VIEW') "
                    + "or hasAuthority('MOHE_QUARTER.ADD') "
                    + "or hasAuthority('MOHE_QUARTER.EDIT') "
                    + "or hasAuthority('MOHE_QUARTER.DELETE') "
                    + "or hasAuthority('MOHE_QUARTER.CLOSE') "
                    + "or hasAuthority('SETTINGS.VIEW') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /** Create a new MOHE quarter (triggers snapshot + advance). */
    public static final String MOHE_QUARTER_ADD =
            "hasAuthority('MOHE_QUARTER.ADD') "
                    + "or hasAuthority('SETTINGS.EDIT') ";

    /** Edit quarter metadata (name, dates, amounts). */
    public static final String MOHE_QUARTER_EDIT =
            "hasAuthority('MOHE_QUARTER.EDIT') "
                    + "or hasAuthority('SETTINGS.EDIT') ";

    /** Close a quarter (archive + advance). */
    public static final String MOHE_QUARTER_CLOSE =
            "hasAuthority('MOHE_QUARTER.CLOSE') "
                    + "or hasAuthority('SETTINGS.EDIT') ";

    /** Delete a quarter record. */
    public static final String MOHE_QUARTER_DELETE =
            "hasAuthority('MOHE_QUARTER.DELETE') "
                    + "or hasAuthority('SETTINGS.EDIT') ";

    /** UMS administration — view/list (aligns with frontend pageCode UMS_ADMIN + UMS_ADMIN.VIEW). */
    public static final String UMS_VIEW =
            "hasAuthority('UMS_ADMIN.VIEW') "
                    + "or hasAuthority('UMS_USERS.VIEW') ";

    /** UMS administration — create (aligns with UMS_ADMIN.ADD). */
    public static final String UMS_ADD =
            "hasAuthority('UMS_ADMIN.ADD')";

    /** UMS administration — update/assign (aligns with UMS_ADMIN.EDIT). */
    public static final String UMS_EDIT =
            "hasAuthority('UMS_ADMIN.EDIT')";

    /** UMS administration — delete (aligns with UMS_ADMIN.DELETE). */
    public static final String UMS_DELETE =
            "hasAuthority('UMS_ADMIN.DELETE')";

    /*
     * The admin tool pages — pages, fields, reports and API links — describe the
     * application itself, not an attaché's staff: the super admin and the ministry
     * admin keep them (DEC-15, review M15). A country admin holds UMS_ADMIN for its
     * own staff and reached these through the same codes.
     */
    private static final String ADMIN_TOOLS_TIER =
            "(hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_MINISTRY_ADMIN'))";

    public static final String ADMIN_TOOLS_VIEW = ADMIN_TOOLS_TIER + " and (" + UMS_VIEW + ")";
    public static final String ADMIN_TOOLS_ADD = ADMIN_TOOLS_TIER + " and " + UMS_ADD;
    public static final String ADMIN_TOOLS_EDIT = ADMIN_TOOLS_TIER + " and " + UMS_EDIT;
    public static final String ADMIN_TOOLS_DELETE = ADMIN_TOOLS_TIER + " and " + UMS_DELETE;

    /* ------------------------------------------------------------------ *
     * Accounting (FINCORE): chart of accounts, fiscal calendar, fund chain.
     *
     * Every accounting controller carries one of these. The WafarController
     * gap found in the 2026-08 review — ten endpoints including approve,
     * reject and delete, reachable by any authenticated user — is not going
     * to be repeated in a module that moves money.
     * ------------------------------------------------------------------ */

    /** Read the chart, the calendar and the balances. */
    public static final String ACCOUNTING_VIEW =
            "hasAuthority('ACCOUNTING.VIEW') "
                    + "or hasAuthority('ACCOUNTING.EDIT') "
                    + "or hasAuthority('FINANCE.VIEW') "
                    + "or hasAuthority('UMS_ADMIN.VIEW') ";

    /**
     * Maintain the chart of accounts.
     *
     * <p>Separate from {@link #ACCOUNTING_VIEW} because the chart is a
     * controlled document: its codes come from the الدليل المحاسبي الموحد, and
     * editing it is not something a general finance user should be able to do.
     *
     * <p>COA.EDIT only (round-5 review, decision D-3). It also accepted ACCOUNTING.EDIT,
     * which V094 gives COUNTRY_ADMIN, so a country admin could edit the ministry's chart
     * and FX rates through the API although the screens offered no button. The ministry
     * keeps it: MINISTRY_ADMIN, SUPER_ADMIN, Admin and Super Admin hold COA.EDIT.
     */
    public static final String COA_EDIT =
            "hasAuthority('COA.EDIT') ";

    /**
     * Create a quarterly or additional allocation to an attaché.
     *
     * <p>ALLOCATION.CREATE only (round-5 QA R5-02, decision D23). The ACCOUNTING.EDIT
     * alternative let MINISTRY_ADMIN, COUNTRY_ADMIN and the Admin / Super Admin /
     * SUPER_ADMIN roles keep creating after V145 took the maker code off them. The
     * super admin still creates: the IsSuperAdmin flag carries every active code (D-2).
     */
    public static final String ALLOCATION_CREATE =
            "hasAuthority('ALLOCATION.CREATE') ";

    /**
     * Approve an allocation, or mark it transferred.
     *
     * <p>Deliberately not the same authority as {@link #ALLOCATION_CREATE}:
     * the officer who proposes a transfer should not be the one who releases
     * it. Whether the two are held by different people is a matter of how
     * roles are assigned, but the system has to make the split possible.
     */
    public static final String ALLOCATION_APPROVE =
            "hasAuthority('ALLOCATION.APPROVE') ";

    /** Close an accounting period or a fiscal year. One-way, and rarely granted. */
    public static final String FISCAL_PERIOD_CLOSE =
            "hasAuthority('FISCAL_PERIOD.CLOSE') ";

    /**
     * Open the next fiscal year and its 13 periods (review F11, plan W4-01). The
     * calendar stopped at the years V040 seeded, and a quarter in any later year
     * could take no allocation or batch. Granted by V126 to the roles that may
     * close periods: whoever keeps the calendar.
     */
    public static final String FISCAL_YEAR_ADD =
            "hasAuthority('FISCAL_YEAR.ADD') ";

    /* ------------------------------------------------------------------ *
     * Accounting screens, round 2 (27 Aug 2026): payment batches, FX rates
     * and budget appropriations. The codes are created by V063 and granted
     * to nobody, following V043.
     * ------------------------------------------------------------------ */

    /**
     * Prepare a payment batch (الدفعة) for an attaché: pick the students and the amounts.
     *
     * <p>PAYMENT_BATCH.CREATE only, for the same reason as {@link #ALLOCATION_CREATE} (R5-02).
     */
    public static final String PAYMENT_BATCH_CREATE =
            "hasAuthority('PAYMENT_BATCH.CREATE') ";

    /**
     * Approve a batch, mark it paid, or cancel it.
     *
     * <p>Kept apart from {@link #PAYMENT_BATCH_CREATE} for the same reason the
     * allocation pair is: the officer who prepares the batch should not be the
     * one who releases the money. {@code PaymentBatchService.approve} also
     * refuses the preparer outright, so holding both authorities is not enough.
     */
    public static final String PAYMENT_BATCH_APPROVE =
            "hasAuthority('PAYMENT_BATCH.APPROVE') ";

    /**
     * Enter or revise the annual appropriation from MOF.
     *
     * <p>Its own code rather than {@link #COA_EDIT}: the chart is structure and
     * the appropriation is money — the figure the whole fund chain draws down.
     * The person who maintains account codes is not necessarily the person who
     * may say how much the ministry was granted this year.
     *
     * <p>BUDGET.EDIT only (round-5 review, decision D-3): the ACCOUNTING.EDIT alternative
     * let a country admin revise the ministry's appropriation through the API. BUDGET.EDIT
     * is held by MINISTRY_ADMIN, SUPER_ADMIN and Finance Manager, and from V146 by Admin
     * and Super Admin, which had reached the budget only through that alternative.
     */
    public static final String BUDGET_EDIT =
            "hasAuthority('BUDGET.EDIT') ";
}
