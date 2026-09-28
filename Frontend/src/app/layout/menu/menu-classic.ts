import { AppMenuItem, MenuFactory } from './menu-model';

/**
 * The sidebar exactly as it shipped before the layout switch existed.
 *
 * Moved here verbatim from AppMenuComponent.initializeMenu() — label for label,
 * icon for icon, order for order. It is deliberately frozen: menu-classic.spec.ts
 * asserts its shape against a checked-in snapshot, so an edit here fails the build
 * unless the snapshot is updated on purpose. New navigation belongs in
 * menu-proposed.ts instead.
 */
export const buildClassicMenu: MenuFactory = (t): AppMenuItem[] => {
    return [
        // ── Dashboard ──────────────────────────────────────────────
        {
            label: t('NAVIGATION.DASHBOARD'),
            icon: 'pi pi-fw pi-home',
            routerLink: ['/']
        },

        // ── Students ───────────────────────────────────────────────
        {
            label: t('NAVIGATION.STUDENTS'),
            icon: 'pi pi-fw pi-id-card',
            items: [
                { label: t('NAVIGATION.ALL_STUDENTS'),           icon: 'pi pi-fw pi-list',          routerLink: ['/students'] },
                { label: t('NAVIGATION.STUDENT_HISTORY'),        icon: 'pi pi-fw pi-history',        routerLink: ['/student-history'] },
                { label: t('NAVIGATION.STUDENT_QUICK_EDIT'),     icon: 'pi pi-fw pi-pencil',         routerLink: ['/student-quick-edit'] },
                { label: t('NAVIGATION.STUDENT_EXTENSION'),      icon: 'pi pi-fw pi-calendar-plus',  routerLink: ['/student-extension'] },
                { label: t('NAVIGATION.STUDENT_TRAVEL_TICKETS'), icon: 'pi pi-fw pi-ticket',         routerLink: ['/student-travel-tickets'] },
                { label: t('NAVIGATION.STUDENT_INFO_REQUESTS'),  icon: 'pi pi-fw pi-envelope',       routerLink: ['/student-info-requests'] },
                { label: t('NAVIGATION.STUDENT_CHANGE_REQUESTS'),icon: 'pi pi-fw pi-file-edit',      routerLink: ['/student-change-requests'] }
            ]
        },

        // ── Accounting System ──────────────────────────────────────
        // Kept separate from Finance on purpose. Finance records what was
        // paid to a student; this is the ledger side — the chart of
        // accounts, the fiscal calendar, and what each attaché holds.
        {
            label: t('NAVIGATION.ACCOUNTING_SYSTEM'),
            icon: 'pi pi-fw pi-book',
            items: [
                { label: t('NAVIGATION.CHART_OF_ACCOUNTS'),     icon: 'pi pi-fw pi-sitemap',    routerLink: ['/accounting/chart-of-accounts'] },
                // The fund chain, in the order the money moves: appropriation,
                // allocation, batch, and what each attaché is left holding.
                { label: t('NAVIGATION.BUDGET_APPROPRIATIONS'), icon: 'pi pi-fw pi-briefcase',  routerLink: ['/accounting/budget-appropriations'] },
                { label: t('NAVIGATION.ALLOCATIONS'),           icon: 'pi pi-fw pi-send',       routerLink: ['/accounting/allocations'] },
                { label: t('NAVIGATION.PAYMENT_BATCHES'),       icon: 'pi pi-fw pi-money-bill', routerLink: ['/accounting/payment-batches'] },
                { label: t('NAVIGATION.ATTACHE_ADVANCES'),      icon: 'pi pi-fw pi-wallet',     routerLink: ['/accounting/advance-balances'] },
                { label: t('NAVIGATION.FX_RATES'),              icon: 'pi pi-fw pi-percentage', routerLink: ['/accounting/fx-rates'] }
            ]
        },

        // ── Finance ────────────────────────────────────────────────
        {
            label: t('NAVIGATION.FINANCE'),
            icon: 'pi pi-fw pi-wallet',
            items: [
                { label: t('NAVIGATION.FINANCE_RECORDS'),         icon: 'pi pi-fw pi-money-bill',    routerLink: ['/finance'] },
                { label: t('NAVIGATION.FINANCE_HISTORY'),         icon: 'pi pi-fw pi-history',       routerLink: ['/studentfinancearch'] },
                { label: t('NAVIGATION.STUDENT_FINANCE_WAFAR'),   icon: 'pi pi-fw pi-star',          routerLink: ['/student-finance-wafar'] },
                { label: t('NAVIGATION.STUDENT_LOAN'),            icon: 'pi pi-fw pi-credit-card',   routerLink: ['/student-loan'] },
                { label: t('NAVIGATION.STUDENT_BOOKS_ALLOWANCE'), icon: 'pi pi-fw pi-book',          routerLink: ['/student-books-allowance'] },
                { label: t('NAVIGATION.WAFAR_SPENDING'),          icon: 'pi pi-fw pi-chart-bar',     routerLink: ['/wafar'] },
                { label: t('NAVIGATION.WAFAR_RECONCILIATION'),    icon: 'pi pi-fw pi-sync',          routerLink: ['/wafar-reconciliation'] },
                { label: t('NAVIGATION.FINANCE_RECEIVED_WHOM'),   icon: 'pi pi-fw pi-users',         routerLink: ['/finance-received-whom'] },
                { label: t('NAVIGATION.PAYMENT_AUTHORIZATION'),   icon: 'pi pi-fw pi-check-circle',  routerLink: ['/payment-authorization'] },
                {
                    label: t('NAVIGATION.UNIVERSITY_FEE'),
                    icon: 'pi pi-fw pi-building',
                    items: [
                        { label: t('NAVIGATION.UNIVERSITY_FEE_MANAGEMENT'), icon: 'pi pi-fw pi-money-bill', routerLink: ['/studyuniversityfee'] },
                        { label: t('NAVIGATION.UNIVERSITY_FEE_HISTORY'),    icon: 'pi pi-fw pi-history',    routerLink: ['/studyuniversityfeearch'] }
                    ]
                }
            ]
        },

        // ── Operations ─────────────────────────────────────────────
        {
            label: t('NAVIGATION.OPERATIONS'),
            icon: 'pi pi-fw pi-cog',
            items: [
                { label: t('NAVIGATION.MOHE_QUARTER'), icon: 'pi pi-fw pi-calendar', routerLink: ['/operations/mohe-quarter'] }
            ]
        },

        // ── Approvals ──────────────────────────────────────────────
        {
            label: t('NAVIGATION.APPROVALS'),
            icon: 'pi pi-fw pi-check-square',
            items: [
                { label: t('NAVIGATION.PENDING_APPROVALS'), icon: 'pi pi-fw pi-clock',   routerLink: ['/approvals/pending'] },
                { label: t('NAVIGATION.APPROVAL_HISTORY'),  icon: 'pi pi-fw pi-history', routerLink: ['/approvals/history'] },
                { label: t('NAVIGATION.MY_REQUESTS'),       icon: 'pi pi-fw pi-send',    routerLink: ['/approvals/my-requests'] },
                { label: t('NAVIGATION.APPROVAL_CONFIGS'),  icon: 'pi pi-fw pi-sliders-h', routerLink: ['/approvals/configs'] }
            ]
        },

        // ── Reports ────────────────────────────────────────────────
        {
            label: t('NAVIGATION.REPORTS'),
            icon: 'pi pi-fw pi-chart-bar',
            items: [
                { label: t('NAVIGATION.REPORTS_LIST'),   icon: 'pi pi-fw pi-file',      routerLink: ['/reports/list'] },
                { label: t('NAVIGATION.GRAPHS'),         icon: 'pi pi-fw pi-chart-bar', routerLink: ['/reports/graphs'] },
                { label: t('NAVIGATION.STUDENT_TRACER'), icon: 'pi pi-fw pi-share-alt', routerLink: ['/reports/statistical/student-tracer'] },
                { label: t('NAVIGATION.STUDENT_GRAPH'),  icon: 'pi pi-fw pi-sitemap',   routerLink: ['/reports/student-graph'] }
            ]
        },

        // ── Notifications ──────────────────────────────────────────
        {
            label: t('NAVIGATION.NOTIFICATIONS'),
            icon: 'pi pi-fw pi-bell',
            items: [
                { label: t('NAVIGATION.SEND_NOTIFICATION'),    icon: 'pi pi-fw pi-send',    routerLink: ['/notifications/send'],
                  permission: 'NOTIFICATIONS.SEND' },
                { label: t('NAVIGATION.NOTIFICATION_HISTORY'), icon: 'pi pi-fw pi-history', routerLink: ['/notifications/history'],
                  permission: 'NOTIFICATIONS.VIEW' },
                { label: t('NAVIGATION.NOTIFICATION_SETTINGS'),icon: 'pi pi-fw pi-cog',     routerLink: ['/notifications/settings'],
                  permission: 'NOTIFICATIONS.SETTINGS' }
            ]
        },

        // ── Alert Events ───────────────────────────────────────────
        {
            label: t('NAVIGATION.ALERT_EVENTS'),
            icon: 'pi pi-fw pi-bell',
            items: [
                { label: t('NAVIGATION.ALERT_CASES'),     icon: 'pi pi-fw pi-exclamation-triangle', routerLink: ['/alert-events/cases'] },
                { label: t('NAVIGATION.ALERT_SCENARIOS'), icon: 'pi pi-fw pi-sliders-h',            routerLink: ['/alert-events/scenarios'] }
            ]
        },

        // ── separator ──────────────────────────────────────────────
        { separator: true },

        // ── Administration ─────────────────────────────────────────
        {
            label: t('NAVIGATION.ADMINISTRATION'),
            icon: 'pi pi-fw pi-shield',
            items: [
                {
                    label: t('NAVIGATION.AUDIT_LOGS'),
                    icon: 'pi pi-fw pi-history',
                    routerLink: ['/audit/logs']
                },
                {
                    label: t('NAVIGATION.SYSTEM_SETTINGS'),
                    icon: 'pi pi-fw pi-wrench',
                    items: [
                        // ── Configuration ───────────────────────────
                        { label: t('NAVIGATION.SYSTEM_SETTINGS_PAGE'), icon: 'pi pi-fw pi-cog',      routerLink: ['/settings/system-settings'], superAdminOnly: true },
                        { label: t('NAVIGATION.REPORT_SETTINGS'),      icon: 'pi pi-fw pi-file-pdf', routerLink: ['/settings/report-settings'] },
                        { label: t('NAVIGATION.CODE_LISTS'),           icon: 'pi pi-fw pi-list',     routerLink: ['/settings/code-lists'] },
                        { label: t('NAVIGATION.SYS_PARAMETERS'),       icon: 'pi pi-fw pi-sliders-h', routerLink: ['/settings/sys-parameters'] },
                        { separator: true },
                        // ── Lookup Tables ───────────────────────────
                        { label: t('NAVIGATION.LU_STUDENT'),          icon: 'pi pi-fw pi-user',       routerLink: ['/settings/lookuptables'], queryParams: { tab: 0 }, routerLinkActiveOptions: { paths: 'exact', queryParams: 'subset', matrixParams: 'ignored', fragment: 'ignored' } },
                        { label: t('NAVIGATION.LU_STUDY_UNIVERSITY'), icon: 'pi pi-fw pi-book',       routerLink: ['/settings/lookuptables'], queryParams: { tab: 1 }, routerLinkActiveOptions: { paths: 'exact', queryParams: 'subset', matrixParams: 'ignored', fragment: 'ignored' } },
                        { label: t('NAVIGATION.LU_FINANCE'),          icon: 'pi pi-fw pi-dollar',     routerLink: ['/settings/lookuptables'], queryParams: { tab: 2 }, routerLinkActiveOptions: { paths: 'exact', queryParams: 'subset', matrixParams: 'ignored', fragment: 'ignored' } },
                        { label: t('NAVIGATION.LU_OPERATIONS'),       icon: 'pi pi-fw pi-cog',        routerLink: ['/settings/lookuptables'], queryParams: { tab: 3 }, routerLinkActiveOptions: { paths: 'exact', queryParams: 'subset', matrixParams: 'ignored', fragment: 'ignored' } },
                        { label: t('NAVIGATION.LU_SYSTEM'),           icon: 'pi pi-fw pi-desktop',    routerLink: ['/settings/lookuptables'], queryParams: { tab: 4 }, routerLinkActiveOptions: { paths: 'exact', queryParams: 'subset', matrixParams: 'ignored', fragment: 'ignored' } },
                        { label: t('NAVIGATION.LU_LOCATION'),         icon: 'pi pi-fw pi-map-marker', routerLink: ['/settings/lookuptables'], queryParams: { tab: 5 }, routerLinkActiveOptions: { paths: 'exact', queryParams: 'subset', matrixParams: 'ignored', fragment: 'ignored' } },
                        { separator: true },
                        // ── Reference Data ──────────────────────────
                        { label: t('NAVIGATION.STUDY_YEARS_LOOKUP'),  icon: 'pi pi-fw pi-calendar',   routerLink: ['/settings/study-years-lookup'] },
                        { label: t('NAVIGATION.STUDY_AMOUNT_LOOKUP'), icon: 'pi pi-fw pi-money-bill', routerLink: ['/settings/study-amount-lookup'] },
                        { separator: true },
                        // ── Finance & Banking ────────────────────────
                        { label: t('NAVIGATION.STUDY_FINANCE_BANK'),        icon: 'pi pi-fw pi-building', routerLink: ['/system-admin/study-finance-bank'] },
                        { label: t('NAVIGATION.STUDY_FINANCE_BANK_BRANCH'), icon: 'pi pi-fw pi-sitemap',  routerLink: ['/system-admin/study-finance-bank-branch'] },
                        { label: t('NAVIGATION.STUDY_WAFAR_TYPE'),          icon: 'pi pi-fw pi-tag',      routerLink: ['/system-admin/study-wafar-type'] },
                        { separator: true },
                        // ── Administration ───────────────────────────
                        { label: t('NAVIGATION.DOCUMENT_TYPES'), icon: 'pi pi-fw pi-file', routerLink: ['/system-admin/document-types'] },
                        { separator: true },
                        // ── System Tools ─────────────────────────────
                        { label: t('NAVIGATION.BACKUP_RESTORE'), icon: 'pi pi-fw pi-database', routerLink: ['/system-admin/backup-restore'] },
                        { label: t('NAVIGATION.DATA_IMPORT'),    icon: 'pi pi-fw pi-upload',   routerLink: ['/system-admin/data-import'] }
                    ]
                },
                {
                    label: t('NAVIGATION.STUDENT_USER_MANAGEMENT'),
                    icon: 'pi pi-fw pi-user-edit',
                    items: [
                        { label: t('NAVIGATION.STUDENT_PORTAL_USERS'),       icon: 'pi pi-fw pi-user',    routerLink: ['/student-admin/users'] },
                        { label: t('NAVIGATION.STUDENT_PORTAL_ROLES'),       icon: 'pi pi-fw pi-key',     routerLink: ['/student-admin/roles'] },
                        { label: t('NAVIGATION.STUDENT_PORTAL_GROUPS'),      icon: 'pi pi-fw pi-users',   routerLink: ['/student-admin/groups'] },
                        { label: t('NAVIGATION.STUDENT_PORTAL_PERMISSIONS'), icon: 'pi pi-fw pi-lock',    routerLink: ['/student-admin/permissions'] },
                        { label: t('NAVIGATION.STUDENT_PORTAL_SESSIONS'),    icon: 'pi pi-fw pi-clock',   routerLink: ['/student-admin/sessions'] }
                    ]
                },
                {
                    label: t('NAVIGATION.STAFF_ADMIN'),
                    icon: 'pi pi-fw pi-users',
                    items: [
                        { label: t('NAVIGATION.USERS'),        icon: 'pi pi-fw pi-user',      routerLink: ['/ums-admin/users'] },
                        { label: t('NAVIGATION.EFFECTIVE_PERMISSIONS'), icon: 'pi pi-fw pi-shield', routerLink: ['/ums-admin/effective-permissions'] },
                        { label: t('NAVIGATION.ACCESS_REVIEW'), icon: 'pi pi-fw pi-verified', routerLink: ['/ums-admin/access-reviews'] },
                        { label: t('NAVIGATION.GROUPS'),       icon: 'pi pi-fw pi-users',     routerLink: ['/ums-admin/groups'] },
                        { label: t('NAVIGATION.ROLES'),        icon: 'pi pi-fw pi-key',       routerLink: ['/ums-admin/roles'] },
                        { label: t('NAVIGATION.PERMISSIONS'),  icon: 'pi pi-fw pi-lock',      routerLink: ['/ums-admin/permissions'] },
                        { label: t('NAVIGATION.PAGES'),        icon: 'pi pi-fw pi-file',      routerLink: ['/ums-admin/pages'], ministryAdminOnly: true },
                        { label: t('NAVIGATION.REPORTS_ADMIN'),icon: 'pi pi-fw pi-chart-bar', routerLink: ['/ums-admin/reports-admin'], ministryAdminOnly: true },
                        { label: t('NAVIGATION.SESSIONS'),     icon: 'pi pi-fw pi-clock',     routerLink: ['/ums-admin/sessions'] },
                        { label: t('NAVIGATION.FIELDS'),       icon: 'pi pi-fw pi-list',      routerLink: ['/ums-admin/fields'], ministryAdminOnly: true },
                        { label: t('NAVIGATION.API_LINKS'),    icon: 'pi pi-fw pi-link',      routerLink: ['/ums-admin/api-links'], ministryAdminOnly: true }
                    ]
                }
            ]
        }
    ];
};
