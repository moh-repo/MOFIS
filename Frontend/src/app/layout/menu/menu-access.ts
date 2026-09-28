import { AppMenuItem } from './menu-model';

/**
 * Who may see which sidebar row: one definition for the sidebar and for the route
 * guard (live QA M5).
 *
 * The guard needs the sidebar's answer. A person who may not open the dashboard is
 * sent to the first row of their own sidebar rather than to Access Denied, and that
 * row is only sure to open if both read the same map and the same rules.
 */

/** What the filter needs to know about the signed-in person. */
export interface MenuAccess {
    /** The page's VIEW right (`UmsPermissionService.canView`). */
    canView(pageCode: string): boolean;
    /** A raw permission code, checked as-is. */
    hasPermission(code: string): boolean;
    isSuperAdmin(): boolean;
    isMinistryAdminOrAbove(): boolean;
}

/** A route's permission data, as UmsPermissionGuard reads it from `route.data`. */
export interface RouteGate {
    pageCode?: string;
    pageCodes?: string[];
    permission?: string;
}

/**
 * Whether the person passes one route's gate: the permission, the page's VIEW right, and
 * the VIEW right of at least one of the pages, each where the route names it. The route
 * guard asks this of every route the router matches, and the sidebar of every route on a
 * row's link (R5-09), so the two cannot answer differently.
 */
export function passesGate(gate: RouteGate, access: Pick<MenuAccess, 'canView' | 'hasPermission'>): boolean {
    if (gate.permission && !access.hasPermission(gate.permission)) {
        return false;
    }
    if (gate.pageCode && !access.canView(gate.pageCode)) {
        return false;
    }
    if (gate.pageCodes?.length && !gate.pageCodes.some(code => access.canView(code))) {
        return false;
    }
    return true;
}

/**
 * Map route to page code(s) for permission check. Aligns with route data.pageCode / pageCodes in app-routing.
 * String value → check canView(code). String[] value → visible if canView() returns true for ANY code.
 * A row must pass the entry of every route on its link, as the router runs the guard of
 * every route it matches: /reports/financial needs '/reports' and '/reports/financial' (R5-09).
 * menu-route-parity.spec.ts checks every row against the real routes.
 * `npm run validate-menu` reads this literal: keep it a plain object under this name.
 */
export const MENU_ROUTE_CODES: { [route: string]: string | string[] } = {
    // Mirrors the home route: the whole dashboard, or any one of its sections, shows the row.
    '/': ['DASHBOARD', 'DASHBOARD_STUDENTS', 'DASHBOARD_FINANCE', 'DASHBOARD_APPROVALS', 'DASHBOARD_ALERTS', 'DASHBOARD_COUNTRIES', 'DASHBOARD_USERS', 'DASHBOARD_QUARTER', 'DASHBOARD_NOTICES'],
    '/students': 'STUDENTS',
    '/students/profile': 'STUDENTS',
    '/student-history': 'STUDENT_HISTORY',
    '/student-quick-edit': 'STUDENT_QUICK_EDIT',
    '/student-extension': 'STUDENT_EXTENSION',
    '/student-travel-tickets': 'STUDENT_TRAVEL',
    '/finance': 'FINANCE',
    '/studentfinancearch': ['FINANCE_ARCH', 'FINANCE'],
    '/student-finance-wafar': 'WAFAR',
    '/studyuniversityfee': 'FINANCE',
    '/studyuniversityfeearch': ['FINANCE_ARCH', 'FINANCE'],
    '/student-loan': 'FINANCE',
    '/student-books-allowance': 'FINANCE',
    '/wafar': 'WAFAR',
    '/wafar-reconciliation': 'WAFAR',
    // ACCOUNTING_VIEW on the server also accepts FINANCE.VIEW.
    '/accounting/chart-of-accounts': ['ACCOUNTING', 'FINANCE'],
    '/accounting/advance-balances': ['ACCOUNTING', 'FINANCE'],
    '/accounting/budget-appropriations': ['ACCOUNTING', 'FINANCE'],
    '/accounting/allocations': ['ACCOUNTING', 'FINANCE'],
    '/accounting/payment-batches': ['ACCOUNTING', 'FINANCE'],
    '/accounting/fx-rates': ['ACCOUNTING', 'FINANCE'],
    '/finance-received-whom': 'FINANCE',
    '/payment-authorization': 'PAYMENT_AUTH',
    '/settings/lookuptables': 'SETTINGS',
    '/settings/study-years-lookup': 'SETTINGS',
    '/settings/study-amount-lookup': 'SETTINGS',
    '/settings/system-settings': 'SETTINGS',
    '/settings/report-settings': 'SETTINGS',
    '/settings/code-lists': 'SETTINGS',
    '/settings/sys-parameters': 'SETTINGS',
    '/system-admin/document-types': 'SETTINGS',
    '/system-admin/study-finance-bank': 'SETTINGS',
    '/system-admin/study-finance-bank-branch': 'SETTINGS',
    '/system-admin/study-wafar-type': 'SETTINGS',
    '/system-admin/backup-restore': 'SETTINGS',
    '/system-admin/data-import': 'SETTINGS',
    '/operations/mohe-quarter': ['MOHE_QUARTER', 'SETTINGS'],
    // The reports route's own gate (app-routing), which every /reports/... row must also pass (R5-09).
    '/reports': 'REPORTS',
    '/reports/list': 'REPORTS',
    '/reports/graphs': 'REPORTS',
    '/reports/student-graph': 'REPORT_ACADEMIC',
    '/reports/student': 'REPORT_ACADEMIC',
    '/reports/financial': 'REPORT_FINANCE',
    '/reports/statistical': 'REPORT_OTHER',
    // The tracer route's own gate (live QA R5-08): its network is student records
    // (STUDENT_PII on the server), so a report reader without them was offered a page
    // of "Access denied" toasts. '/reports' and '/reports/statistical' still apply.
    '/reports/statistical/student-tracer': ['STUDENTS', 'STUDENTS_LIST', 'STUDENTS_ADD', 'STUDENT_QUICK_EDIT', 'STUDENT_HISTORY', 'UMS_ADMIN'],
    // Was missing entirely: the menu is fail-closed, so an Operations
    // reports row would have been hidden from everyone, superadmin
    // included, until this entry existed.
    '/reports/operations': 'REPORT_OTHER',
    '/reports/periodic': 'REPORT_OTHER',
    '/reports/university': 'REPORT_OTHER',
    '/reports/custom': 'REPORT_OTHER',
    '/audit/logs': 'AUDIT',
    '/approvals/pending': 'APPROVALS',
    '/approvals/history': 'APPROVALS',
    // Whoever raises requests sees their own (R5-16); approvals.module's MY_REQUESTS_PAGE_CODES.
    '/approvals/my-requests': ['APPROVALS', 'STUDENTS', 'FINANCE', 'WAFAR'],
    '/approvals/configs': 'APPROVALS',
    '/student-info-requests': 'STUDENT_INFO_REQUESTS',
    '/student-change-requests': 'STUDENTS',
    '/student-admin/users':       'STUDENTS',
    '/student-admin/roles':       'STUDENTS',
    '/student-admin/groups':      'STUDENTS',
    '/student-admin/permissions': 'STUDENTS',
    '/student-admin/sessions':    'STUDENTS',
    '/ums-admin/users': 'UMS_ADMIN',
    // R-13 (phase 2) and S-04 (phase 4): both were missing here, which hid them from every user.
    '/ums-admin/effective-permissions': 'UMS_ADMIN',
    '/ums-admin/access-reviews': 'ACCESS_REVIEW',
    '/ums-admin/groups': 'UMS_ADMIN',
    '/ums-admin/roles': 'UMS_ADMIN',
    '/ums-admin/permissions': 'UMS_ADMIN',
    '/ums-admin/pages': 'UMS_ADMIN',
    '/ums-admin/reports-admin': 'UMS_ADMIN',
    '/ums-admin/sessions': 'UMS_ADMIN',
    '/ums-admin/fields': 'UMS_ADMIN',
    '/ums-admin/api-links': 'UMS_ADMIN',
    '/notifications/send': 'NOTIFICATIONS',
    '/notifications/history': 'NOTIFICATIONS',
    '/notifications/settings': 'NOTIFICATIONS',
    '/alert-events/cases': 'ALERT_EVENTS',
    '/alert-events/scenarios': 'ALERT_EVENTS'
};

/** A routerLink as one normalised path: ['/settings/lookuptables'] and ['settings','lookuptables'] both -> '/settings/lookuptables'. */
function routePath(route: string[] | undefined): string {
    return '/' + (route || []).join('/').replace(/^\/+|\/+$/g, '').replace(/\/+/g, '/');
}

/**
 * The gates a routerLink has to pass, outermost first: the entry of every route on its path
 * that the map names, ending with the link's own (or `own`, a row's pageCode, in its place).
 * Null when the link's own route is not in the map.
 */
export function gatesForRoute(route: string[] | undefined, own?: string | string[]): RouteGate[] | null {
    const segments = routePath(route).split('/').filter(Boolean);
    const ownCodes = own ?? MENU_ROUTE_CODES[routePath(route)];
    if (!ownCodes) {
        return null;
    }
    const outer = segments.slice(0, -1)
        .map((_, i) => MENU_ROUTE_CODES['/' + segments.slice(0, i + 1).join('/')])
        .filter((codes): codes is string | string[] => !!codes);
    return [...outer, ownCodes].map(codes => Array.isArray(codes) ? { pageCodes: codes } : { pageCode: codes });
}

/** The tree the person may see: rows they may not open, empty groups and stray separators removed. */
export function visibleMenu(items: AppMenuItem[], access: MenuAccess): AppMenuItem[] {
    return tidySeparators(
        items
            .map(item => visibleItem(item, access))
            .filter((item): item is AppMenuItem => item !== null)
    );
}

/**
 * Check if menu item should be visible based on permissions
 * Returns a new item with filtered children if needed
 */
function visibleItem(item: AppMenuItem, access: MenuAccess): AppMenuItem | null {
    // Handle separator items
    if (item.separator) {
        return item;
    }

    // The system settings page (name, logo) is the super admin's alone (review M2):
    // SETTINGS.VIEW opened it to every settings officer, and its save was refused.
    if (item.superAdminOnly && !access.isSuperAdmin()) {
        return null;
    }
    // The admin tool pages are the super admin's and the ministry admin's (DEC-15).
    if (item.ministryAdminOnly && !access.isMinistryAdminOrAbove()) {
        return null;
    }

    // A raw permission code wins over the route lookup: it is the only way
    // to express a gate the backend states as an action rather than a page
    // view, and getting it wrong shows a row the server will refuse.
    if (item.permission) {
        return access.hasPermission(item.permission) ? item : null;
    }

    // If item has routerLink, check permission (fail closed: hide if no mapping or no permission)
    if (item.routerLink) {
        const gates = gatesForRoute(item.routerLink, item.pageCode);
        if (!gates) {
            // Route has no permission mapping - hide for security (don't show unmapped items)
            return null;
        }
        if (!gates.every(gate => passesGate(gate, access))) {
            return null; // Item not visible
        }
    }

    // If item has children, filter them recursively
    if (item.items && item.items.length > 0) {
        const filteredChildren = visibleMenu(item.items, access);

        // If no destinations are visible, hide the parent. Separators are
        // not destinations: a group left holding nothing but its own rules
        // has nowhere to go, and must not survive as an empty heading.
        if (!filteredChildren.some(child => !child.separator)) {
            return null;
        }

        // Return item with filtered children
        return {
            ...item,
            items: filteredChildren
        };
    }

    // Default: show if no permission check needed
    return item;
}

/**
 * Drops separators that no longer separate anything.
 *
 * A separator earns its place by sitting between two visible things. Once
 * permission filtering has removed its neighbours it can end up leading,
 * trailing, or doubled against another separator, and renders as a rule with
 * nothing on one side of it.
 */
export function tidySeparators(items: AppMenuItem[]): AppMenuItem[] {
    const kept: AppMenuItem[] = [];
    for (const item of items) {
        const stranded = item.separator &&
            (kept.length === 0 || kept[kept.length - 1].separator);
        if (!stranded) {
            kept.push(item);
        }
    }
    while (kept.length && kept[kept.length - 1].separator) {
        kept.pop();
    }
    return kept;
}

/**
 * The first row of a visible tree that leads to a page, depth first: the top of the
 * person's sidebar. Never the home page, which is what was refused. Null when the
 * tree leads nowhere.
 */
export function firstPermittedLink(items: AppMenuItem[]): AppMenuItem | null {
    for (const item of items) {
        if (item.separator) continue;
        if (item.routerLink && routePath(item.routerLink) !== '/') return item;
        const nested = item.items?.length ? firstPermittedLink(item.items) : null;
        if (nested) return nested;
    }
    return null;
}
