/**
 * What each code list of `/api/v1/code-lists/names` actually is.
 *
 * The API hands the screen sixty-odd slugs (`alert-severity`, `boolean-filter`,
 * `ums-role-kind` …) and nothing else: no title, no purpose, no idea which of
 * them an operator is allowed to touch. This file supplies the missing half —
 * the domain the list belongs to (the picker's groups) and, for a list this
 * screen only reads, the screen that does own its table.
 *
 * The human title and the one-line purpose are translations, keyed by the slug
 * in upper snake case: `SETTINGS.CODE_LISTS.LIST_NAMES.ALERT_SEVERITY` and
 * `SETTINGS.CODE_LISTS.LIST_DESCRIPTIONS.ALERT_SEVERITY`. That is the idiom of
 * the sibling lookup-tables screen (`LOOKUP_TABLES.TABLE_NAMES.*`).
 *
 * A list the registry gains and this file has not caught up with is not lost:
 * it falls into the `OTHER` group under its own slug.
 */

/** The picker's groups, in display order. */
export type CodeListGroup =
    | 'ACCESS'
    | 'APPROVALS'
    | 'STUDENTS'
    | 'FINANCE'
    | 'ALERTS'
    | 'REPORTS'
    | 'SYSTEM'
    | 'OTHER';

export const CODE_LIST_GROUPS: CodeListGroup[] =
    ['ACCESS', 'APPROVALS', 'STUDENTS', 'FINANCE', 'ALERTS', 'REPORTS', 'SYSTEM', 'OTHER'];

/** The screen that maintains a list this one only reads. */
export interface OwningScreen {
    route: string;
    queryParams?: Record<string, string | number>;
    /** Translation keys, joined into a breadcrumb: "System Coding › Student". */
    labelKeys: string[];
}

export interface CodeListCatalogEntry {
    group: CodeListGroup;
    screen?: OwningScreen;
}

const lookupTables = (tab: number, table: string, tabLabelKey: string): OwningScreen => ({
    route: '/settings/lookuptables',
    queryParams: { tab, table },
    labelKeys: ['NAVIGATION.LOOKUP_TABLES', tabLabelKey]
});

export const CODE_LIST_CATALOG: Record<string, CodeListCatalogEntry> = {
    // ── Users & access ───────────────────────────────────────────────────────
    'ums-user-status': { group: 'ACCESS' },
    'ums-user-privilege': { group: 'ACCESS' },
    'ums-role-kind': { group: 'ACCESS' },
    'ums-group-kind': { group: 'ACCESS' },
    'ums-field-type': { group: 'ACCESS' },
    'permission-resource-type': { group: 'ACCESS' },
    'permission-action-type': { group: 'ACCESS' },
    'http-method': { group: 'ACCESS' },
    'portal-account-status': { group: 'ACCESS' },

    // ── Approvals & requests ─────────────────────────────────────────────────
    'request-type': { group: 'APPROVALS' },
    'approval-status': { group: 'APPROVALS' },
    'approval-action': { group: 'APPROVALS' },
    'approval-entity-type': {
        group: 'APPROVALS',
        screen: { route: '/approvals/configs', labelKeys: ['NAVIGATION.APPROVAL_CONFIGS'] }
    },
    'change-request-status': { group: 'APPROVALS' },
    'change-request-field': { group: 'APPROVALS' },
    'info-request-status': { group: 'APPROVALS' },
    'info-review-status': { group: 'APPROVALS' },
    'inquiry-type': { group: 'APPROVALS' },
    'inquiry-status': { group: 'APPROVALS' },

    // ── Students & study ─────────────────────────────────────────────────────
    // `study-status` reads STUDENT_STUDY_STATUS — the lookup screen's own
    // "study-status" is a different table (STUDY_STATUS), hence the exact id.
    'study-status': { group: 'STUDENTS', screen: lookupTables(0, 'student-study-status', 'NAVIGATION.LU_STUDENT') },
    'ministry-type': { group: 'STUDENTS', screen: lookupTables(0, 'student-ministry-type', 'NAVIGATION.LU_STUDENT') },
    'university-type': { group: 'STUDENTS', screen: lookupTables(1, 'student-university-type', 'NAVIGATION.LU_STUDY_UNIVERSITY') },
    'fin-received-status': { group: 'STUDENTS' },
    'pay-fee-status': { group: 'STUDENTS' },
    'extension-type': { group: 'STUDENTS' },
    'extension-status': { group: 'STUDENTS' },
    'travel-type': { group: 'STUDENTS' },
    'death-procedure': { group: 'STUDENTS' },
    'death-case-status': { group: 'STUDENTS' },
    'semester-number': { group: 'STUDENTS' },
    'academic-year': { group: 'STUDENTS' },

    // ── Finance ──────────────────────────────────────────────────────────────
    'payment-method': { group: 'FINANCE' },
    'payment-auth-status': { group: 'FINANCE' },
    'payment-amount-source': { group: 'FINANCE' },
    'payment-batch-status': { group: 'FINANCE' },
    'allocation-type': { group: 'FINANCE' },
    'allocation-status': { group: 'FINANCE' },
    'appropriation-status': { group: 'FINANCE' },
    'account-nature': { group: 'FINANCE' },
    'fiscal-year-status': { group: 'FINANCE' },
    'fiscal-period-status': { group: 'FINANCE' },
    'loan-status': { group: 'FINANCE' },
    'books-allowance-pay-status': { group: 'FINANCE' },
    'university-fee-status': { group: 'FINANCE' },
    'fee-source': { group: 'FINANCE' },
    'fee-check-payor': { group: 'FINANCE' },
    'fee-type': { group: 'FINANCE' },
    'wafar-spending-status': { group: 'FINANCE' },
    'wafar-reconciliation-status': { group: 'FINANCE' },
    'wafar-beneficiary-type': { group: 'FINANCE' },
    'quarter-number': { group: 'FINANCE' },

    // ── Alerts & notifications ───────────────────────────────────────────────
    'alert-severity': { group: 'ALERTS' },
    'alert-status': { group: 'ALERTS' },
    'alert-type': { group: 'ALERTS' },
    'notice-status': { group: 'ALERTS' },
    'notification-channel': { group: 'ALERTS' },
    'notification-recipient-type': { group: 'ALERTS' },
    'notification-scope': { group: 'ALERTS' },
    'notification-send-status': { group: 'ALERTS' },

    // ── Reports & charts ─────────────────────────────────────────────────────
    'report-label': { group: 'REPORTS' },
    'graph-category': { group: 'REPORTS' },

    // ── System ───────────────────────────────────────────────────────────────
    'boolean-filter': { group: 'SYSTEM' },
    'visibility-filter': { group: 'SYSTEM' },
    'status-active': { group: 'SYSTEM' },
    'yes-no': { group: 'SYSTEM' },
    'schedule-status': { group: 'SYSTEM' },
    'run-status': { group: 'SYSTEM' }
};

/** `alert-severity` → `ALERT_SEVERITY`, the suffix of the list's two translation keys. */
export function codeListKey(name: string): string {
    return name.toUpperCase().replace(/-/g, '_');
}

/** The catalogue entry of a list; an unknown list is grouped under `OTHER`. */
export function describeCodeList(name: string): CodeListCatalogEntry {
    return CODE_LIST_CATALOG[name] ?? { group: 'OTHER' };
}
