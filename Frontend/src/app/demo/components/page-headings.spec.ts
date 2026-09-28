import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';

import { ApprovalsModule } from './approvals/approvals.module';
import { PendingApprovalsComponent } from './approvals/pending-approvals/pending-approvals.component';
import { ApprovalHistoryComponent } from './approvals/approval-history/approval-history.component';
import { MyRequestsComponent } from './approvals/my-requests/my-requests.component';
import { ApprovalConfigsComponent } from './approvals/approval-configs/approval-configs.component';
import { StudentInfoRequestsModule } from './student-info-requests/student-info-requests.module';
import { StudentInfoRequestsComponent } from './student-info-requests/student-info-requests.component';
import { StudentChangeRequestsModule } from './student-change-requests/student-change-requests.module';
import { StudentChangeRequestsComponent } from './student-change-requests/student-change-requests.component';
import { AlertEventsModule } from './alert-events/alert-events.module';
import { AlertEventsCasesComponent } from './alert-events/alert-events-cases/alert-events-cases.component';
import { AlertEventsScenariosComponent } from './alert-events/alert-events-scenarios/alert-events-scenarios.component';
import { NotificationsModule } from './notifications/notifications.module';
import { NotificationHistoryComponent } from './notifications/notification-history/notification-history.component';
import { SendNotificationComponent } from './notifications/send-notification/send-notification.component';
import { NotificationSettingsComponent } from './notifications/notification-settings/notification-settings.component';
import { SettingsModule } from './settings.module';
import { StudyYearsLookupComponent } from './study-years-lookup/study-years-lookup.component';
import { StudyAmountLookupComponent } from './study-amount-lookup/study-amount-lookup.component';
import { SystemSettingsComponent } from './settings/system-settings/system-settings.component';
import { ReportSettingsComponent } from './settings/report-settings/report-settings.component';
import { CodeListsComponent } from './settings/code-lists/code-lists.component';
import { SysParametersComponent } from './settings/sys-parameters/sys-parameters.component';
import { LookupTablesModule } from './settings/lookuptables/lookuptables.module';
import { LookupTablesComponent } from './settings/lookuptables/lookuptables.component';
import { SystemAdministrationModule } from './system-administration/system-administration.module';
import { DocumentTypesComponent } from './system-administration/document-types/document-types.component';
import { StudyFinanceBankComponent } from './system-administration/study-finance-bank/study-finance-bank.component';
import { StudyFinanceBankBranchComponent } from './system-administration/study-finance-bank-branch/study-finance-bank-branch.component';
import { StudyWafarTypeComponent } from './system-administration/study-wafar-type/study-wafar-type.component';
import { StudentAdminModule } from './student-admin/student-admin.module';
import { StudentUsersComponent } from './student-admin/student-users/student-users.component';
import { StudentRolesComponent } from './student-admin/student-roles/student-roles.component';
import { StudentGroupsComponent } from './student-admin/student-groups/student-groups.component';
import { StudentPermissionsComponent } from './student-admin/student-permissions/student-permissions.component';
import { StudentSessionsComponent } from './student-admin/student-sessions/student-sessions.component';
import { AccountingModule } from './accounting/accounting.module';
import { ChartOfAccountsComponent } from './accounting/chart-of-accounts/chart-of-accounts.component';
import { AdvanceBalancesComponent } from './accounting/advance-balances/advance-balances.component';
import { BudgetAppropriationsComponent } from './accounting/budget-appropriations/budget-appropriations.component';
import { AllocationsComponent } from './accounting/allocations/allocations.component';
import { PaymentBatchesComponent } from './accounting/payment-batches/payment-batches.component';
import { FxRatesComponent } from './accounting/fx-rates/fx-rates.component';
import { AccountingApiService } from './accounting/accounting-api.service';
import { MoheQuarterService } from '../service/mohe-quarter.service';
import { AuditLogModule } from './audit/audit-log.module';
import { AuditLogComponent } from './audit/audit-log.component';
import { UserManagementComponent } from './ums-admin/user-management/user-management.component';
import { GroupManagementComponent } from './ums-admin/group-management/group-management.component';
import { RoleManagementComponent } from './ums-admin/role-management/role-management.component';
import { PermissionManagementComponent } from './ums-admin/permission-management/permission-management.component';
import { PageManagementComponent } from './ums-admin/page-management/page-management.component';
import { ReportManagementComponent } from './ums-admin/report-management/report-management.component';
import { SessionManagementComponent } from './ums-admin/session-management/session-management.component';
import { FieldManagementComponent } from './ums-admin/field-management/field-management.component';
import { ApiLinkManagementComponent } from './ums-admin/api-link-management/api-link-management.component';
import { ReportsModule } from './reports/reports.module';
import { ReportsListComponent } from './reports/reports-list/reports-list.component';
import { GraphsComponent } from './reports/graphs/graphs.component';
import { BarChartsComponent } from './reports/bar-charts/bar-charts.component';
import { LineChartsComponent } from './reports/line-charts/line-charts.component';
import { StudentReportsComponent } from './reports/student-reports/student-reports.component';
import { IndividualStudentReportsComponent } from './reports/student-reports/individual-student-reports/individual-student-reports.component';
import { FinancialReportsComponent } from './reports/financial-reports/financial-reports.component';
import { StatisticalReportsComponent } from './reports/statistical-reports/statistical-reports.component';
import { PeriodicReportsComponent } from './reports/periodic-reports/periodic-reports.component';
import { UniversityReportsComponent } from './reports/university-reports/university-reports.component';
import { CustomReportsComponent } from './reports/custom-reports/custom-reports.component';
import { OperationsReportsComponent } from './reports/operations-reports/operations-reports.component';
import { AuditTrailReportComponent } from './reports/operations-reports/audit-trail/audit-trail.component';

import { ApprovalService } from '../service/approval.service';
import { StudentInfoRequestService } from '../service/student-info-request.service';
import { StudentdataService } from '../service/studentdata.service';
import { StudentTravelTicketService } from '../service/student-travel-ticket.service';
import { EnumOptionsService } from '../service/enum-options.service';
import { LookupService } from '../service/lookup.service';
import { TranslationService } from '../service/translation.service';
import { UmsPermissionService } from '../service/ums-permission.service';
import { UmsAuthService } from '../service/ums-auth.service';
import { UmsGroupService } from '../service/ums-group.service';
import { UmsUserService } from '../service/ums-user.service';
import { UmsRoleService } from '../service/ums-role.service';
import { ReportCatalogService } from '../service/report-catalog.service';
import { AuthService } from '../service/auth.service';
import { AlertEventsService } from '../service/alert-events.service';
import { NotificationService } from '../service/notification.service';
import { ReportCountryScopeService } from '../service/report-country-scope.service';
import { CodeListService } from '../service/code-list.service';
import { StudyYearsLookupApiService } from '../service/study-years-lookup.service';
import { StudyAmountLookupApiService } from '../service/study-amount-lookup.service';
import { SysParameterService } from '../service/sys-parameter.service';
import { ReportSettingsService } from '../service/report-settings.service';
import { LookupTableService } from '../service/lookuptable.service';
import { SystemSettingsService } from '../service/system-settings.service';
import { DocumentTypeService } from '../service/document-type.service';
import { StudyFinanceBankService } from '../service/study-finance-bank.service';
import { StudyFinanceBankBranchService } from '../service/study-finance-bank-branch.service';
import { StudyWafarTypeService } from '../service/study-wafar-type.service';
import { AuditLogService } from '../api/auditlog';
import { ReportPdfService } from '../service/report-pdf.service';
import { ApiUrlService } from '../service/api-url.service';
import { ChartPrintService } from '../service/chart-print.service';
import { ChartDataService } from '../service/chart-data.service';
import { codeListStub } from '../../testing/code-list.stub';
import { provideUmsTestIcons } from '../../testing/nz-icons.stub';

/**
 * Where the page heading sits, and what sits opposite it.
 *
 * <p>In Arabic the heading of /approvals/pending sat on the left, against the
 * text it introduces. The cause was a double mirror, not a missing one. Under
 * `direction: rtl` a flex row already runs right to left, so
 * `justify-content-between` puts the first child at the right edge without
 * help; styles.scss then reverses the row again for every
 * `.flex.justify-content-between` under `[dir="rtl"]`, which put the heading
 * back on the left and its companion — a record count, a Refresh button, a
 * whole button group — on the right. Pages written from the same copied header
 * were all wrong the same way, which is why this file covers menus rather than
 * a page: Approvals and Requests first, then Alerts and Notifications, then
 * Settings — which had the same fault twice over, once inherited from
 * styles.scss and once written by hand in report-settings.component.scss —
 * then System administration, four pages off one copied header, and the
 * Student portal access pages, four of which carried the class and one of
 * which had no header row at all.
 *
 * <p>They now separate the two ends with `flex-1` on the heading block instead.
 * That lays out identically in English and matches nothing in styles.scss, so
 * the row mirrors once, as it should. The pages that never used the class —
 * student change requests, send notification, notification settings — are here
 * to hold that they stay correct, not because anything changed in them.
 *
 * <p>The assertions are geometric rather than class names, because a class name
 * would not have caught this: the markup read correctly and the stylesheet was
 * what moved the box. They also avoid naming the widgets, so a page that swaps
 * a count for a button is still covered.
 */

/** Any method returns an empty observable; enough to get a page rendered. */
const quiet = () => new Proxy({} as any, { get: () => () => of([]) });

const page = () => of({ content: [], totalElements: 0 });

/**
 * An empty list that is also an empty page. The Staff-access pages ask one
 * service for a page of records and another for the whole list, and hand the
 * first straight to `.content` and the second straight to `.map`; a value
 * that is both keeps every call on the happy path without knowing which is
 * which.
 */
const pageOrList = () => of(Object.assign([] as any[], { content: [], totalElements: 0, totalPages: 0 }));
const pageOrListProxy = () => new Proxy({} as any, { get: () => () => pageOrList() });

/** Flipped per case before the component is built; most pages read it for `dir`. */
let rtl = false;

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => rtl, translate: (k: string) => k, getCurrentLanguage: () => 'en'
};

const commonProviders = [
    MessageService,
    ConfirmationService,
    // The Staff-access pages carry nz-modal, which animates, and nz-icons,
    // which a stubbed HttpClient cannot fetch.
    provideNoopAnimations(),
    provideUmsTestIcons(),
    { provide: HttpClient, useValue: { get: () => page(), post: () => of({}), put: () => of({}) } },
    { provide: ApprovalService, useValue: new Proxy({} as any, { get: () => () => page() }) },
    { provide: StudentInfoRequestService, useValue: new Proxy({} as any, { get: () => () => page() }) },
    { provide: StudentdataService, useValue: { getPagingAll: () => page(), getAll: () => of([]), getPendingStudentIds: () => of([]) } },
    { provide: StudentTravelTicketService, useValue: quiet() },
    { provide: EnumOptionsService, useValue: quiet() },
    { provide: LookupService, useValue: quiet() },
    { provide: UmsGroupService, useValue: quiet() },
    {
        // Not `quiet()`: the notification pages read `selectedCountryId$` as a
        // stream and `selectedCountryId` as a value, and a proxy that answers
        // every property with a function turns both into one.
        provide: ReportCountryScopeService,
        useValue: new Proxy({ selectedCountryId$: of(null), selectedCountryId: null } as any, {
            get: (target, prop) => prop in target ? target[prop] : () => of([])
        })
    },
    { provide: CodeListService, useValue: codeListStub() },
    { provide: AlertEventsService, useValue: new Proxy({} as any, { get: () => () => page() }) },
    { provide: NotificationService, useValue: new Proxy({} as any, { get: () => () => page() }) },
    {
        provide: UmsAuthService,
        // `permissions$` is a stream the *hasPermission directive subscribes
        // to, not a method; the alert scenarios buttons are behind it.
        useValue: new Proxy({ permissions$: of([]) } as any, {
            get: (target, prop) => {
                if (prop in target) { return target[prop]; }
                if (prop === 'getCurrentUser') { return () => ({ id: 1, username: 'tester' }); }
                // Takes a list and gives back what the user may see; a
                // permissive stub hands it straight back.
                if (prop === 'filterToAllowedStudyCountries') { return (list: any[]) => list ?? []; }
                return () => true;
            }
        })
    },
    { provide: AuthService, useValue: { getCurrentUser: () => ({ id: 1 }) } },
    {
        provide: UmsPermissionService,
        useValue: new Proxy({} as any, {
            get: (_t, prop) => prop === 'getAllowedCountryIds' ? () => [] : () => true
        })
    },
    { provide: TranslationService, useValue: translation },

    // ── Settings ────────────────────────────────────────────────────────────
    { provide: StudyYearsLookupApiService, useValue: quiet() },
    { provide: StudyAmountLookupApiService, useValue: quiet() },
    { provide: SysParameterService, useValue: quiet() },
    { provide: ReportSettingsService, useValue: quiet() },
    { provide: LookupTableService, useValue: quiet() },
    { provide: SystemSettingsService, useValue: quiet() },

    // ── System administration ───────────────────────────────────────────────
    { provide: DocumentTypeService, useValue: quiet() },
    { provide: StudyFinanceBankService, useValue: quiet() },
    { provide: StudyFinanceBankBranchService, useValue: quiet() },
    { provide: StudyWafarTypeService, useValue: quiet() },

    // ── Audit logs ──────────────────────────────────────────────────────────
    { provide: AuditLogService, useValue: { getAuditLogs: () => page(), getDistinctTableNames: () => of([]), getDistinctUsers: () => of([]) } },

    // -- Reports -------------------------------------------------------------
    // The pdf and url services hand back strings that templates put straight
    // into attributes, so a proxy answering everything with an observable is
    // the wrong shape for those two.
    { provide: ReportPdfService, useValue: new Proxy({} as any, { get: () => () => '' }) },
    { provide: ApiUrlService, useValue: { apiPath: (path: string) => path, getBaseUrl: () => '' } },
    { provide: ChartPrintService, useValue: new Proxy({} as any, { get: () => () => undefined }) },
    { provide: ChartDataService, useValue: quiet() }
];

interface Case {
    name: string;
    component: any;
    imports: any[];
    /** Overrides for a page whose shape the common stubs do not fit. */
    providers?: any[];
}

const CASES: Case[] = [
    // ── Approvals and Requests ──────────────────────────────────────────────
    { name: 'pending approvals', component: PendingApprovalsComponent, imports: [ApprovalsModule, RouterModule.forRoot([])] },
    { name: 'approval history', component: ApprovalHistoryComponent, imports: [ApprovalsModule, RouterModule.forRoot([])] },
    { name: 'my requests', component: MyRequestsComponent, imports: [ApprovalsModule, RouterModule.forRoot([])] },
    {
        name: 'approval rules', component: ApprovalConfigsComponent,
        imports: [ApprovalsModule, RouterModule.forRoot([])],
        // The rules page lists configurations rather than a page of requests,
        // and iterates what it is handed — from the service for the rules and
        // straight from HttpClient for the roles. The shared page-shaped stubs
        // are not iterables.
        providers: [
            { provide: ApprovalService, useValue: quiet() },
            { provide: HttpClient, useValue: { get: () => of([]), post: () => of({}), put: () => of({}) } }
        ]
    },
    { name: 'student info requests', component: StudentInfoRequestsComponent, imports: [StudentInfoRequestsModule, RouterModule.forRoot([])] },
    { name: 'student change requests', component: StudentChangeRequestsComponent, imports: [StudentChangeRequestsModule, RouterModule.forRoot([])] },

    // ── Alerts and Notifications ────────────────────────────────────────────
    { name: 'alert cases', component: AlertEventsCasesComponent, imports: [AlertEventsModule, RouterModule.forRoot([])] },
    {
        name: 'alert scenarios', component: AlertEventsScenariosComponent,
        imports: [AlertEventsModule, RouterModule.forRoot([])],
        // The scenarios page lists scenarios, not a page of cases.
        providers: [{ provide: AlertEventsService, useValue: quiet() }]
    },
    { name: 'notification history', component: NotificationHistoryComponent, imports: [NotificationsModule, RouterModule.forRoot([])] },
    {
        name: 'send notification', component: SendNotificationComponent,
        imports: [NotificationsModule, RouterModule.forRoot([])],
        providers: [{ provide: NotificationService, useValue: quiet() }]
    },
    {
        name: 'notification settings', component: NotificationSettingsComponent,
        imports: [NotificationsModule, RouterModule.forRoot([])],
        providers: [{ provide: NotificationService, useValue: quiet() }]
    },

    // ── Settings ────────────────────────────────────────────────────────────
    { name: 'study years lookup', component: StudyYearsLookupComponent, imports: [SettingsModule, RouterModule.forRoot([])] },
    { name: 'study amount lookup', component: StudyAmountLookupComponent, imports: [SettingsModule, RouterModule.forRoot([])] },
    {
        name: 'code lists', component: CodeListsComponent,
        imports: [SettingsModule, RouterModule.forRoot([])],
        // The page asks the code-list service for the names of every list. The
        // shared stub is a real CodeListService warmed from a payload with no
        // HTTP behind it, so that one call has to be answered here rather than
        // replacing the whole service — the template's pipes still need it.
        providers: [{ provide: CodeListService, useValue: withNames(codeListStub()) }]
    },
    { name: 'system parameters', component: SysParametersComponent, imports: [SettingsModule, RouterModule.forRoot([])] },
    { name: 'report settings', component: ReportSettingsComponent, imports: [SettingsModule, RouterModule.forRoot([])] },
    { name: 'lookup tables', component: LookupTablesComponent, imports: [LookupTablesModule, RouterModule.forRoot([])] },
    // The system settings page never carried `justify-content-between`: its
    // heading is an icon and a title with nothing opposite. Here so that giving
    // it a button brings it under the rule rather than past it.
    { name: 'system settings', component: SystemSettingsComponent, imports: [SettingsModule, RouterModule.forRoot([])] },

    // ── System administration ───────────────────────────────────────────────
    // Four pages from one copied header, wrong the same way. Backup/restore and
    // data import are not here: they are ng-zorro cards with an `nzTitle`, not a
    // heading in a flex row, and mirror through a different mechanism entirely.
    { name: 'document types', component: DocumentTypesComponent, imports: [SystemAdministrationModule, RouterModule.forRoot([])] },
    { name: 'finance banks', component: StudyFinanceBankComponent, imports: [SystemAdministrationModule, RouterModule.forRoot([])] },
    { name: 'finance bank branches', component: StudyFinanceBankBranchComponent, imports: [SystemAdministrationModule, RouterModule.forRoot([])] },
    { name: 'wafar types', component: StudyWafarTypeComponent, imports: [SystemAdministrationModule, RouterModule.forRoot([])] },

    // ── Student portal access ───────────────────────────────────────────────
    // Users and sessions are paged, so the shared HttpClient stub fits them;
    // roles, groups and permissions hand the response straight to the table
    // as a list, and a page object is not one.
    { name: 'student users', component: StudentUsersComponent, imports: [StudentAdminModule, RouterModule.forRoot([])] },
    { name: 'student sessions', component: StudentSessionsComponent, imports: [StudentAdminModule, RouterModule.forRoot([])] },
    { name: 'student roles', component: StudentRolesComponent, imports: [StudentAdminModule, RouterModule.forRoot([])], providers: [listHttp()] },
    { name: 'student groups', component: StudentGroupsComponent, imports: [StudentAdminModule, RouterModule.forRoot([])], providers: [listHttp()] },
    { name: 'student permissions', component: StudentPermissionsComponent, imports: [StudentAdminModule, RouterModule.forRoot([])], providers: [listHttp()] },

    // ── Accounting ──────────────────────────────────────────────────────────
    // Six pages from one copied header, wrong the same way, and the reason this
    // menu is here: in Arabic the title sat on the left and the Add button on
    // the right. Advance balances has nothing opposite its heading and is here
    // so that giving it a button brings it under the rule rather than past it.
    { name: 'chart of accounts', component: ChartOfAccountsComponent, imports: [AccountingModule, RouterModule.forRoot([])], providers: accountingProviders() },
    { name: 'advance balances', component: AdvanceBalancesComponent, imports: [AccountingModule, RouterModule.forRoot([])], providers: accountingProviders() },
    { name: 'budget appropriations', component: BudgetAppropriationsComponent, imports: [AccountingModule, RouterModule.forRoot([])], providers: accountingProviders() },
    { name: 'attache allocations', component: AllocationsComponent, imports: [AccountingModule, RouterModule.forRoot([])], providers: accountingProviders() },
    { name: 'payment batches', component: PaymentBatchesComponent, imports: [AccountingModule, RouterModule.forRoot([])], providers: accountingProviders() },
    { name: 'fx rates', component: FxRatesComponent, imports: [AccountingModule, RouterModule.forRoot([])], providers: accountingProviders() },

    // ── Audit logs ──────────────────────────────────────────────────────────
    // Was a p-card with its own header styled from the component, which the
    // spec could not see at all; now the same block as every other page.
    { name: 'audit logs', component: AuditLogComponent, imports: [AuditLogModule, RouterModule.forRoot([])] },

    // ── Staff access ────────────────────────────────────────────────────────
    // Standalone ng-zorro components. Their heading was an nz-card title with
    // the buttons in nzExtra — no heading element, nothing this spec could
    // measure; now the same block as every other page, over an nz-table.
    { name: 'staff users', component: UserManagementComponent, imports: [UserManagementComponent, RouterModule.forRoot([])], providers: umsProviders() },
    { name: 'staff groups', component: GroupManagementComponent, imports: [GroupManagementComponent, RouterModule.forRoot([])], providers: umsProviders() },
    { name: 'staff roles', component: RoleManagementComponent, imports: [RoleManagementComponent, RouterModule.forRoot([])], providers: umsProviders() },
    { name: 'staff permissions', component: PermissionManagementComponent, imports: [PermissionManagementComponent, RouterModule.forRoot([])], providers: umsProviders() },
    { name: 'staff pages', component: PageManagementComponent, imports: [PageManagementComponent, RouterModule.forRoot([])], providers: umsProviders() },
    { name: 'staff reports', component: ReportManagementComponent, imports: [ReportManagementComponent, RouterModule.forRoot([])], providers: umsProviders() },
    // Sessions had no nzExtra at all — no button anywhere on the page; it has Refresh now.
    { name: 'staff sessions', component: SessionManagementComponent, imports: [SessionManagementComponent, RouterModule.forRoot([])], providers: umsProviders() },
    { name: 'staff fields', component: FieldManagementComponent, imports: [FieldManagementComponent, RouterModule.forRoot([])], providers: umsProviders() },
    { name: 'staff api links', component: ApiLinkManagementComponent, imports: [ApiLinkManagementComponent, RouterModule.forRoot([])], providers: umsProviders() },

    // -- Reports -------------------------------------------------------------
    // Fifty-seven report templates carried `justify-content-between` on a row
    // the heading leads -- every page under the menu, in six categories off a
    // handful of copied headers. These are one page of each shape: the hub of
    // every category, a leaf from each, both flex-wrap variants, the one that
    // goes to a column at small widths, and a chart page whose header is styled
    // from its own stylesheet rather than from the utilities.
    { name: 'reports list', component: ReportsListComponent, imports: [ReportsModule, RouterModule.forRoot([])], providers: reportProviders() },
    { name: 'graphs', component: GraphsComponent, imports: [ReportsModule, RouterModule.forRoot([])], providers: reportProviders() },
    { name: 'bar charts', component: BarChartsComponent, imports: [ReportsModule, RouterModule.forRoot([])], providers: reportProviders() },
    { name: 'line charts', component: LineChartsComponent, imports: [ReportsModule, RouterModule.forRoot([])], providers: reportProviders() },
    { name: 'student reports hub', component: StudentReportsComponent, imports: [ReportsModule, RouterModule.forRoot([])], providers: reportProviders() },
    { name: 'individual student reports', component: IndividualStudentReportsComponent, imports: [ReportsModule, RouterModule.forRoot([])], providers: reportProviders() },
    { name: 'financial reports hub', component: FinancialReportsComponent, imports: [ReportsModule, RouterModule.forRoot([])], providers: reportProviders() },
    { name: 'statistical reports hub', component: StatisticalReportsComponent, imports: [ReportsModule, RouterModule.forRoot([])], providers: reportProviders() },
    { name: 'periodic reports hub', component: PeriodicReportsComponent, imports: [ReportsModule, RouterModule.forRoot([])], providers: reportProviders() },
    { name: 'university reports hub', component: UniversityReportsComponent, imports: [ReportsModule, RouterModule.forRoot([])], providers: reportProviders() },
    { name: 'custom reports hub', component: CustomReportsComponent, imports: [ReportsModule, RouterModule.forRoot([])], providers: reportProviders() },
    { name: 'operations reports hub', component: OperationsReportsComponent, imports: [ReportsModule, RouterModule.forRoot([])], providers: reportProviders() },
    { name: 'audit trail report', component: AuditTrailReportComponent, imports: [ReportsModule, RouterModule.forRoot([])], providers: reportProviders() }

    // Nine leaf report pages are deliberately absent: enrollment statistics,
    // budget analysis, wafar statement, payment auth report, growth analysis,
    // monthly report, fee structure, data visualization and the quarterly
    // financial report each render a `@for` over a collection taken straight
    // from their response, and Angular's reconcile will not iterate the stubbed
    // one. Their headers took the identical transformation as the pages here —
    // the whole point of the change is that all fifty-seven are one shape — and
    // covering them needs a data-shaped stub per page, not a layout fix.,
];

/**
 * A report response: an empty list, an empty page, and an empty list under any
 * field name a page cares to read. The report pages pull a dozen differently
 * named arrays out of their responses -- `labels`, `datasets`, `rows`,
 * `categories` -- and a page that crashes on one of them never renders, which a
 * layout spec would then report as a layout failure, and the nesting has to go
 * all the way down for the same reason. `then` is excluded on purpose: an
 * object that answers it looks like a promise to rxjs.
 */
function emptyish(): any {
    return new Proxy(Object.assign([] as any[], { totalElements: 0, totalPages: 0 }), {
        get: (target, prop) => {
            if (prop in target) { return (target as any)[prop]; }
            if (typeof prop === 'symbol' || prop === 'then' || prop === 'toJSON') { return undefined; }
            // Nested on purpose: `response.data.rows` has to answer too, and an
            // ordinary empty array would give `undefined` at the second step.
            return emptyish();
        }
    });
}

const reportResponse = () => of(emptyish());

/**
 * The report pages read the catalogue, the country scope and the chart data
 * through services that are thin wrappers over HttpClient. Stubbing those by
 * hand means growing a method every time a page calls a new one — and a stub of
 * the wrong shape crashes the page before it renders, which a layout spec then
 * reports as a layout failure. The real classes go in instead, over an
 * HttpClient that answers everything with an empty list.
 */
function reportProviders() {
    return [
        { provide: HttpClient, useValue: { get: () => reportResponse(), post: () => reportResponse(), put: () => of({}), delete: () => of({}) } },
        { provide: ReportCountryScopeService, useClass: ReportCountryScopeService },
        { provide: ChartDataService, useClass: ChartDataService },
        { provide: ReportCatalogService, useClass: ReportCatalogService }
    ];
}

/** The Staff-access services, every method an empty page-or-list; HttpClient the same, for the pages that call it directly. */
function umsProviders() {
    return [
        { provide: HttpClient, useValue: { get: () => pageOrList(), post: () => of({}), put: () => of({}), delete: () => of({}) } },
        { provide: UmsUserService, useValue: pageOrListProxy() },
        { provide: UmsGroupService, useValue: pageOrListProxy() },
        { provide: UmsRoleService, useValue: pageOrListProxy() },
        { provide: UmsPermissionService, useValue: pageOrListProxy() },
        { provide: ReportCatalogService, useValue: { getCatalog: () => of([]), reload: () => {} } }
    ];
}

/** An HttpClient whose every GET is an empty list, for the pages that iterate what they fetch. */
/**
 * The two services the accounting pages ask for on load. `quiet()` answers every
 * call with an empty list, which is all these pages need to render a header — but
 * the chart's summary is read as an object, and a list is not one.
 */
function accountingProviders() {
    return [
        {
            provide: AccountingApiService,
            useValue: new Proxy({} as any, {
                get: (_t, prop) => prop === 'chartSummary'
                    ? () => of({ accountCount: 0, postableCount: 0 })
                    : () => of([])
            })
        },
        { provide: MoheQuarterService, useValue: quiet() }
    ];
}

function listHttp() {
    return { provide: HttpClient, useValue: { get: () => of([]), post: () => of({}), put: () => of({}), delete: () => of({}) } };
}

/** The code-list service stub, plus the one call the code-lists page makes on load. */
function withNames(service: CodeListService): CodeListService {
    (service as any).getNames = () => of([]);
    return service;
}

/** The box the glyphs occupy, which for a full-width block is not the element's. */
function textRect(el: Element): DOMRect {
    const range = document.createRange();
    range.selectNodeContents(el);
    return range.getBoundingClientRect();
}

/**
 * Pages whose heading has no icon in front of it.
 *
 * <p>Measured, not assumed: the check below was written for the four Approvals
 * pages and run across all twenty-eight, which found five more. Three of those —
 * lookup tables, student info requests and student change requests — were fixed
 * on the owner's next request and have come off this list. The two that remain
 * are both under Reports, and nobody has asked for them.
 *
 * <p>Named here rather than left out of the sweep, so that giving one an icon is
 * a matter of deleting a line, and so that the list is a fact on the record
 * rather than a silence.
 */
const NO_ICON_YET = new Set([
    'audit trail report',
    'individual student reports'
]);

CASES.forEach(({ name, component, imports, providers }) => {

    describe(`page heading on ${name}`, () => {

        let fixture: any;
        let host: HTMLElement;

        const build = async (dir: 'ltr' | 'rtl') => {
            rtl = dir === 'rtl';

            TestBed.resetTestingModule();
            await TestBed.configureTestingModule({
                imports,
                providers: [...commonProviders, ...(providers ?? [])]
            }).compileComponents();

            fixture = TestBed.createComponent(component);
            fixture.detectChanges();
            await fixture.whenStable();

            // The class and the attribute both: the layout wrapper carries the
            // pair, and rtl.css keys off `.rtl` while styles.scss keys off
            // `[dir="rtl"]`. A test that set only one would miss half the rules.
            host = document.createElement('div');
            host.setAttribute('dir', dir);
            if (rtl) { host.className = 'rtl'; }
            document.body.appendChild(host);
            host.appendChild(fixture.nativeElement);

            fixture.detectChanges();
        };

        afterEach(() => {
            host?.remove();
            rtl = false;
        });

        /** The card the heading is in — the header card on every page here. */
        const card = () => fixture.nativeElement.querySelector('.card') as HTMLElement;
        const headingOf = (c: HTMLElement) => c.querySelector('h1, h2, h3, h4, h5') as HTMLElement;

        /**
         * The heading's row: the outermost flex ancestor inside the card.
         *
         * By computed style rather than by the `flex` utility class, and
         * outermost rather than nearest. Both matter. Report settings lays its
         * header out from its own stylesheet, so a search for the class found no
         * row at all and measured the bare heading — which sits 32px in behind a
         * 2rem icon, in both directions, and failed as if the page were wrong.
         * And where the heading is wrapped with an icon in a nested flex block —
         * the alert, notification and code-list pages — the nearest flex ancestor
         * is that inner block, whose last child is the heading's own; the
         * companion check then took the "nothing opposite" branch and never
         * looked at the button group actually sitting at the far end.
         */
        const rowOf = (heading: HTMLElement, c: HTMLElement): HTMLElement | null => {
            let row: HTMLElement | null = null;
            for (let node = heading.parentElement; node && node !== c && c.contains(node); node = node.parentElement) {
                const display = getComputedStyle(node).display;
                if (display === 'flex' || display === 'inline-flex') { row = node; }
            }
            return row;
        };

        /**
         * What leads and trails the heading.
         *
         * Some headings are the row's first child; others are wrapped with an
         * icon, a subtitle, or both. What has to sit at the reading-start edge
         * is whichever element leads the row, so that is what is measured — and
         * a trailing element only counts as a companion if it is not part of
         * the heading's own block.
         */
        const parts = (c: HTMLElement) => {
            const heading = headingOf(c);
            const row = rowOf(heading, c);
            if (!row) { return { heading, row: null, leading: heading, companion: null }; }

            const children = Array.from(row.children) as HTMLElement[];
            const last = children[children.length - 1];
            return {
                heading,
                row,
                leading: children[0],
                companion: last.contains(heading) ? null : last
            };
        };

        /** How far `el` is from the card's content edge on the reading-start side. */
/**
         * How far the block sits from the card's content edge on the reading-start
         * side, less whatever the wrappers in between reserve.
         *
         * The audit-trail report puts its header inside a `.card-header`, which
         * has 18.88px of padding of its own; measured from the card alone that
         * page failed by the same 18.88px in *both* directions — symmetric, and
         * therefore not a mirror fault at all, the same false alarm report
         * settings produced. Every wrapper between the card and the block is
         * counted, so the number this returns is the block's own indent inside
         * its row. Pages whose row is a direct child of the card, which is most
         * of them, are unaffected: a flex utility row reserves nothing.
         */
        const insetFromStart = (dir: 'ltr' | 'rtl', el: Element) => {
            const c = card();
            const style = getComputedStyle(c);
            const box = c.getBoundingClientRect();
            const rect = el.getBoundingClientRect();

            let reserved = 0;
            for (let node = el.parentElement; node && node !== c && c.contains(node); node = node.parentElement) {
                const s = getComputedStyle(node);
                reserved += dir === 'ltr'
                    ? parseFloat(s.borderLeftWidth) + parseFloat(s.paddingLeft)
                    : parseFloat(s.borderRightWidth) + parseFloat(s.paddingRight);
            }

            return (dir === 'ltr'
                ? rect.left - (box.left + parseFloat(style.borderLeftWidth) + parseFloat(style.paddingLeft))
                : (box.right - parseFloat(style.borderRightWidth) - parseFloat(style.paddingRight)) - rect.right) - reserved;
        };

        it('starts the heading block at the left edge in English', async () => {
            await build('ltr');

            const inset = insetFromStart('ltr', parts(card()).leading);
            expect(inset).withContext(`heading block is ${inset}px from the card's left content edge`)
                .toBeLessThan(8);
            expect(inset).withContext(`heading block overhangs the card by ${-inset}px`)
                .toBeGreaterThan(-2);
        });

        it('starts the heading block at the right edge in Arabic', async () => {
            await build('rtl');

            const inset = insetFromStart('rtl', parts(card()).leading);
            expect(inset).withContext(`heading block is ${inset}px from the card's right content edge`)
                .toBeLessThan(8);
            expect(inset).withContext(`heading block overhangs the card by ${-inset}px`)
                .toBeGreaterThan(-2);
        });

        it('leaves the heading nearer the edge it reads from than the one it reads to', async () => {
            // The checks above pin the block against a computed edge, which a
            // future change to the card's padding could make brittle. This one
            // cannot be satisfied by a heading on the wrong side whatever the
            // padding is, and is the assertion that actually failed before. It
            // measures the glyphs, since a block heading fills the width in
            // both directions and only its text says which edge it is anchored
            // to.
            for (const dir of ['ltr', 'rtl'] as const) {
                await build(dir);

                const box = card().getBoundingClientRect();
                const text = textRect(headingOf(card()));

                const fromStart = dir === 'ltr' ? text.left - box.left : box.right - text.right;
                const fromEnd = dir === 'ltr' ? box.right - text.right : text.left - box.left;

                expect(fromStart)
                    .withContext(`${dir}: heading is ${fromStart}px from the start edge and ${fromEnd}px from the end`)
                    .toBeLessThan(fromEnd);

                host.remove();
            }
        });

        (NO_ICON_YET.has(name) ? xit : it)('leads the heading with an icon', async () => {
            // Every page in the application announces itself with one. The four
            // Approvals pages led with the words alone, which is what made that
            // menu look unlike the rest at a glance.
            await build('ltr');

            const heading = headingOf(card());
            const scope = rowOf(heading, card()) ?? heading.parentElement!;

            // Anywhere in the heading's row, so long as it comes before the
            // heading: the icon is sometimes the row's own first child and
            // sometimes sits inside the block that wraps the title with it.
            const icon = (Array.from(scope.querySelectorAll('i[class*="pi-"], span[nz-icon], .p-avatar, svg')) as Element[])
                .find(el => !el.contains(heading)
                    && (heading.compareDocumentPosition(el) & Node.DOCUMENT_POSITION_PRECEDING) !== 0);

            expect(icon)
                .withContext(`${name} has no icon in front of its heading — the row reads `
                    + `"${(scope.textContent ?? '').trim().replace(/\s+/g, ' ').slice(0, 50)}"`)
                .toBeTruthy();
        });

        it('puts whatever shares the heading row at the far end of it', async () => {
            // The count on the three request lists, Add configuration on
            // approval rules, Create request on student info requests, Refresh
            // on notification history, the button group on the two alert pages.
            // They are the other half of the same layout: if the row is
            // mirrored twice they come back to the heading's own side.
            for (const dir of ['ltr', 'rtl'] as const) {
                await build(dir);

                const { row, companion } = parts(card());

                if (!companion) {
                    // Student change requests has a plain block heading; send
                    // notification and notification settings have an icon and a
                    // title and nothing opposite. Stated rather than skipped
                    // silently, so that giving one a companion brings it under
                    // the rule rather than passing by omission.
                    expect(row === null || row.lastElementChild!.contains(headingOf(card())))
                        .withContext(`${name} grew a heading companion; it now needs the check below`).toBeTrue();
                    host.remove();
                    continue;
                }

                const box = row!.getBoundingClientRect();
                const trailing = companion.getBoundingClientRect();
                const style = getComputedStyle(companion);

                // Its own trailing margin is placement, not misplacement, and
                // it is not symmetric: rtl.css:146 gives every success button a
                // margin-left of 0.5rem in Arabic and nothing in English, so
                // Create request and Add configuration stop just short of the
                // edge in Arabic only. Subtracted rather than tolerated, so
                // this measures which end the companion is at and says nothing
                // about the gap.
                const margin = parseFloat(dir === 'ltr' ? style.marginRight : style.marginLeft);
                const gap = dir === 'ltr' ? box.right - trailing.right : trailing.left - box.left;

                expect(gap - margin)
                    .withContext(`${dir}: companion is ${gap}px from the far edge, of which ${margin}px is its own margin`)
                    .toBeLessThan(2);

                host.remove();
            }
        });
    });
});
