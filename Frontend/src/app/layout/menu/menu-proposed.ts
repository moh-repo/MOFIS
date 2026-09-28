import { AppMenuItem, MenuFactory } from './menu-model';

/**
 * The reorganised sidebar — seven sections, each named for a job someone does
 * rather than for a subsystem. This is the structure from the Sidebar Wayfinding
 * proposal, row for row.
 *
 * What moved, and why:
 *  - Accounting folds into Finance. It was a two-row top-level section sitting
 *    beside Finance's eleven, and the source needed a comment to justify the
 *    split — which is the tell that the boundary wasn't self-evident.
 *  - The three scattered Wafar rows become one group, as do the two university
 *    fee rows.
 *  - MOHE Quarter moves into Students (it is already gated STUDENTS) so that
 *    "Operations" names only one thing: the reports category.
 *  - The two request queues move to Approvals & Requests. The daily job is
 *    working a queue, and the section is already named for it.
 *  - Notifications and Alert Events were two adjacent pi-bell sections; they
 *    become one section with five distinct icons.
 *  - Every report category gets a row, including Operations, so the reports
 *    built recently are reachable in two clicks rather than by drilling.
 *  - The six lookup rows that only differed by ?tab= collapse to one. The page
 *    already renders those six tabs and owns the query param.
 *
 * The seven report categories reuse the REPORTS_LIST.* labels the reports hub
 * already ships in both languages, rather than duplicating them under NAVIGATION.
 */
export const buildProposedMenu: MenuFactory = (t): AppMenuItem[] => {
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
                { label: t('NAVIGATION.ALL_STUDENTS'),          icon: 'pi pi-fw pi-list',         routerLink: ['/students'] },
                { label: t('NAVIGATION.QUICK_EDIT'),            icon: 'pi pi-fw pi-pencil',       routerLink: ['/student-quick-edit'] },
                { label: t('NAVIGATION.STUDENT_HISTORY'),       icon: 'pi pi-fw pi-history',      routerLink: ['/student-history'] },
                { label: t('NAVIGATION.EXTENSIONS'),            icon: 'pi pi-fw pi-calendar-plus', routerLink: ['/student-extension'] },
                { label: t('NAVIGATION.TRAVEL_TICKETS'),        icon: 'pi pi-fw pi-ticket',       routerLink: ['/student-travel-tickets'] },
                { label: t('NAVIGATION.QUARTERLY_DATA_CYCLE'),  icon: 'pi pi-fw pi-calendar',     routerLink: ['/operations/mohe-quarter'] }
            ]
        },

        // ── Finance ────────────────────────────────────────────────
        {
            label: t('NAVIGATION.FINANCE'),
            icon: 'pi pi-fw pi-wallet',
            items: [
                { label: t('NAVIGATION.FINANCE_RECORDS'),       icon: 'pi pi-fw pi-money-bill',   routerLink: ['/finance'] },
                { label: t('NAVIGATION.FINANCE_HISTORY'),       icon: 'pi pi-fw pi-history',      routerLink: ['/studentfinancearch'] },
                { label: t('NAVIGATION.STUDENT_LOANS'),         icon: 'pi pi-fw pi-credit-card',  routerLink: ['/student-loan'] },
                { label: t('NAVIGATION.BOOKS_ALLOWANCE'),       icon: 'pi pi-fw pi-book',         routerLink: ['/student-books-allowance'] },
                { label: t('NAVIGATION.RECEIVED_BY'),           icon: 'pi pi-fw pi-users',        routerLink: ['/finance-received-whom'] },
                { label: t('NAVIGATION.PAYMENT_AUTHORIZATION'), icon: 'pi pi-fw pi-check-circle', routerLink: ['/payment-authorization'] },
                {
                    label: t('NAVIGATION.UNIVERSITY_FEES'),
                    icon: 'pi pi-fw pi-building',
                    items: [
                        { label: t('NAVIGATION.FEE_MANAGEMENT'), icon: 'pi pi-fw pi-money-bill', routerLink: ['/studyuniversityfee'] },
                        { label: t('NAVIGATION.FEE_HISTORY'),    icon: 'pi pi-fw pi-history',    routerLink: ['/studyuniversityfeearch'] }
                    ]
                },
                {
                    label: t('NAVIGATION.WAFAR'),
                    icon: 'pi pi-fw pi-star',
                    items: [
                        { label: t('NAVIGATION.STUDENT_WAFAR'),  icon: 'pi pi-fw pi-user',      routerLink: ['/student-finance-wafar'] },
                        { label: t('NAVIGATION.WAFAR_SPENDING'), icon: 'pi pi-fw pi-chart-bar', routerLink: ['/wafar'] },
                        { label: t('NAVIGATION.RECONCILIATION'), icon: 'pi pi-fw pi-sync',      routerLink: ['/wafar-reconciliation'] }
                    ]
                },
                {
                    label: t('NAVIGATION.ACCOUNTING'),
                    icon: 'pi pi-fw pi-book',
                    items: [
                        { label: t('NAVIGATION.CHART_OF_ACCOUNTS'),     icon: 'pi pi-fw pi-sitemap',    routerLink: ['/accounting/chart-of-accounts'] },
                        { label: t('NAVIGATION.BUDGET_APPROPRIATIONS'), icon: 'pi pi-fw pi-briefcase',  routerLink: ['/accounting/budget-appropriations'] },
                        { label: t('NAVIGATION.ALLOCATIONS'),           icon: 'pi pi-fw pi-send',       routerLink: ['/accounting/allocations'] },
                        { label: t('NAVIGATION.PAYMENT_BATCHES'),       icon: 'pi pi-fw pi-money-bill', routerLink: ['/accounting/payment-batches'] },
                        { label: t('NAVIGATION.ATTACHE_ADVANCES'),      icon: 'pi pi-fw pi-wallet',     routerLink: ['/accounting/advance-balances'] },
                        { label: t('NAVIGATION.FX_RATES'),              icon: 'pi pi-fw pi-percentage', routerLink: ['/accounting/fx-rates'] }
                    ]
                }
            ]
        },

        // ── Approvals & Requests ───────────────────────────────────
        {
            label: t('NAVIGATION.APPROVALS_AND_REQUESTS'),
            icon: 'pi pi-fw pi-check-square',
            items: [
                { label: t('NAVIGATION.PENDING_APPROVALS'),        icon: 'pi pi-fw pi-clock',     routerLink: ['/approvals/pending'] },
                { label: t('NAVIGATION.APPROVAL_HISTORY'),         icon: 'pi pi-fw pi-history',   routerLink: ['/approvals/history'] },
                { label: t('NAVIGATION.MY_REQUESTS'),              icon: 'pi pi-fw pi-send',      routerLink: ['/approvals/my-requests'] },
                { label: t('NAVIGATION.STUDENT_INFO_REQUESTS'),    icon: 'pi pi-fw pi-envelope',  routerLink: ['/student-info-requests'] },
                { label: t('NAVIGATION.STUDENT_CHANGE_REQUESTS'),  icon: 'pi pi-fw pi-file-edit', routerLink: ['/student-change-requests'] },
                { label: t('NAVIGATION.APPROVAL_RULES'),           icon: 'pi pi-fw pi-sliders-h', routerLink: ['/approvals/configs'] }
            ]
        },

        // ── Reports ────────────────────────────────────────────────
        {
            label: t('NAVIGATION.REPORTS'),
            icon: 'pi pi-fw pi-chart-bar',
            items: [
                { label: t('NAVIGATION.ALL_REPORTS'),              icon: 'pi pi-fw pi-th-large',    routerLink: ['/reports/list'] },
                { label: t('REPORTS_LIST.STUDENT_REPORTS'),        icon: 'pi pi-fw pi-users',       routerLink: ['/reports/student'] },
                { label: t('REPORTS_LIST.FINANCIAL_REPORTS'),      icon: 'pi pi-fw pi-money-bill',  routerLink: ['/reports/financial'] },
                { label: t('REPORTS_LIST.OPERATIONS_REPORTS'),     icon: 'pi pi-fw pi-cog',         routerLink: ['/reports/operations'] },
                { label: t('REPORTS_LIST.STATISTICAL_REPORTS'),    icon: 'pi pi-fw pi-chart-line',  routerLink: ['/reports/statistical'] },
                { label: t('REPORTS_LIST.PERIODIC_REPORTS'),       icon: 'pi pi-fw pi-calendar',    routerLink: ['/reports/periodic'] },
                { label: t('REPORTS_LIST.UNIVERSITY_REPORTS'),     icon: 'pi pi-fw pi-building',    routerLink: ['/reports/university'] },
                { label: t('REPORTS_LIST.CUSTOM_REPORTS'),         icon: 'pi pi-fw pi-filter',      routerLink: ['/reports/custom'] },
                {
                    label: t('NAVIGATION.CHARTS_AND_TRACERS'),
                    icon: 'pi pi-fw pi-chart-pie',
                    items: [
                        { label: t('NAVIGATION.GRAPHS'),         icon: 'pi pi-fw pi-chart-bar', routerLink: ['/reports/graphs'] },
                        { label: t('NAVIGATION.STUDENT_GRAPH'),  icon: 'pi pi-fw pi-sitemap',   routerLink: ['/reports/student-graph'] },
                        { label: t('NAVIGATION.STUDENT_TRACER'), icon: 'pi pi-fw pi-share-alt', routerLink: ['/reports/statistical/student-tracer'] }
                    ]
                }
            ]
        },

        // ── Alerts & Notifications ─────────────────────────────────
        {
            label: t('NAVIGATION.ALERTS_AND_NOTIFICATIONS'),
            icon: 'pi pi-fw pi-bell',
            items: [
                { label: t('NAVIGATION.ALERT_CASES'),           icon: 'pi pi-fw pi-exclamation-triangle', routerLink: ['/alert-events/cases'] },
                { label: t('NAVIGATION.ALERT_SCENARIOS'),       icon: 'pi pi-fw pi-sliders-h',            routerLink: ['/alert-events/scenarios'] },
                { label: t('NAVIGATION.SEND_NOTIFICATION'),     icon: 'pi pi-fw pi-send',                 routerLink: ['/notifications/send'],
                  permission: 'NOTIFICATIONS.SEND' },
                { label: t('NAVIGATION.NOTIFICATION_HISTORY'),  icon: 'pi pi-fw pi-inbox',                routerLink: ['/notifications/history'],
                  permission: 'NOTIFICATIONS.VIEW' },
                { label: t('NAVIGATION.NOTIFICATION_SETTINGS'), icon: 'pi pi-fw pi-cog',                  routerLink: ['/notifications/settings'],
                  permission: 'NOTIFICATIONS.SETTINGS' }
            ]
        },

        // ── Administration ─────────────────────────────────────────
        {
            label: t('NAVIGATION.ADMINISTRATION'),
            icon: 'pi pi-fw pi-shield',
            items: [
                { label: t('NAVIGATION.AUDIT_LOGS'), icon: 'pi pi-fw pi-history', routerLink: ['/audit/logs'] },
                {
                    label: t('NAVIGATION.SETTINGS'),
                    icon: 'pi pi-fw pi-wrench',
                    items: [
                        { label: t('NAVIGATION.SYSTEM_SETTINGS_PAGE'), icon: 'pi pi-fw pi-cog',        routerLink: ['/settings/system-settings'], superAdminOnly: true },
                        { label: t('NAVIGATION.REPORT_SETTINGS'),      icon: 'pi pi-fw pi-file-pdf',   routerLink: ['/settings/report-settings'] },
                        { label: t('NAVIGATION.CODE_LISTS'),           icon: 'pi pi-fw pi-list',       routerLink: ['/settings/code-lists'] },
                        { label: t('NAVIGATION.SYS_PARAMETERS'),       icon: 'pi pi-fw pi-sliders-h',  routerLink: ['/settings/sys-parameters'] },
                        { label: t('NAVIGATION.LOOKUP_TABLES'),        icon: 'pi pi-fw pi-table',      routerLink: ['/settings/lookuptables'] },
                        { label: t('NAVIGATION.STUDY_YEARS'),          icon: 'pi pi-fw pi-calendar',   routerLink: ['/settings/study-years-lookup'] },
                        { label: t('NAVIGATION.STUDY_AMOUNTS'),        icon: 'pi pi-fw pi-money-bill', routerLink: ['/settings/study-amount-lookup'] },
                        { label: t('NAVIGATION.DOCUMENT_TYPES'),       icon: 'pi pi-fw pi-file',       routerLink: ['/system-admin/document-types'] }
                    ]
                },
                {
                    label: t('NAVIGATION.BANKING_AND_WAFAR_TYPES'),
                    icon: 'pi pi-fw pi-building',
                    items: [
                        { label: t('NAVIGATION.BANKS'),         icon: 'pi pi-fw pi-building', routerLink: ['/system-admin/study-finance-bank'] },
                        { label: t('NAVIGATION.BANK_BRANCHES'), icon: 'pi pi-fw pi-sitemap',  routerLink: ['/system-admin/study-finance-bank-branch'] },
                        { label: t('NAVIGATION.WAFAR_TYPES'),   icon: 'pi pi-fw pi-tag',      routerLink: ['/system-admin/study-wafar-type'] }
                    ]
                },
                {
                    label: t('NAVIGATION.STAFF_ACCESS'),
                    icon: 'pi pi-fw pi-users',
                    items: [
                        { label: t('NAVIGATION.USERS'),         icon: 'pi pi-fw pi-user',      routerLink: ['/ums-admin/users'] },
                        { label: t('NAVIGATION.EFFECTIVE_PERMISSIONS'), icon: 'pi pi-fw pi-shield', routerLink: ['/ums-admin/effective-permissions'] },
                        { label: t('NAVIGATION.ACCESS_REVIEW'), icon: 'pi pi-fw pi-verified', routerLink: ['/ums-admin/access-reviews'] },
                        { label: t('NAVIGATION.GROUPS'),        icon: 'pi pi-fw pi-users',     routerLink: ['/ums-admin/groups'] },
                        { label: t('NAVIGATION.ROLES'),         icon: 'pi pi-fw pi-key',       routerLink: ['/ums-admin/roles'] },
                        { label: t('NAVIGATION.PERMISSIONS'),   icon: 'pi pi-fw pi-lock',      routerLink: ['/ums-admin/permissions'] },
                        { label: t('NAVIGATION.PAGES'),         icon: 'pi pi-fw pi-file',      routerLink: ['/ums-admin/pages'], ministryAdminOnly: true },
                        { label: t('NAVIGATION.REPORTS_ADMIN'), icon: 'pi pi-fw pi-chart-bar', routerLink: ['/ums-admin/reports-admin'], ministryAdminOnly: true },
                        { label: t('NAVIGATION.SESSIONS'),      icon: 'pi pi-fw pi-clock',     routerLink: ['/ums-admin/sessions'] },
                        { label: t('NAVIGATION.FIELDS'),        icon: 'pi pi-fw pi-list',      routerLink: ['/ums-admin/fields'], ministryAdminOnly: true },
                        { label: t('NAVIGATION.API_LINKS'),     icon: 'pi pi-fw pi-link',      routerLink: ['/ums-admin/api-links'], ministryAdminOnly: true }
                    ]
                },
                {
                    label: t('NAVIGATION.STUDENT_PORTAL_ACCESS'),
                    icon: 'pi pi-fw pi-user-edit',
                    items: [
                        { label: t('NAVIGATION.PORTAL_USERS'),       icon: 'pi pi-fw pi-user',  routerLink: ['/student-admin/users'] },
                        { label: t('NAVIGATION.PORTAL_ROLES'),       icon: 'pi pi-fw pi-key',   routerLink: ['/student-admin/roles'] },
                        { label: t('NAVIGATION.PORTAL_GROUPS'),      icon: 'pi pi-fw pi-users', routerLink: ['/student-admin/groups'] },
                        { label: t('NAVIGATION.PORTAL_PERMISSIONS'), icon: 'pi pi-fw pi-lock',  routerLink: ['/student-admin/permissions'] },
                        { label: t('NAVIGATION.PORTAL_SESSIONS'),    icon: 'pi pi-fw pi-clock', routerLink: ['/student-admin/sessions'] }
                    ]
                },
                {
                    label: t('NAVIGATION.SYSTEM_TOOLS'),
                    icon: 'pi pi-fw pi-server',
                    items: [
                        { label: t('NAVIGATION.BACKUP_RESTORE'), icon: 'pi pi-fw pi-database', routerLink: ['/system-admin/backup-restore'] },
                        { label: t('NAVIGATION.DATA_IMPORT'),    icon: 'pi pi-fw pi-upload',   routerLink: ['/system-admin/data-import'] }
                    ]
                }
            ]
        }
    ];
};
