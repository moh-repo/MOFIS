import { RouterModule } from '@angular/router';
import { NgModule } from '@angular/core';
import { NotfoundComponent } from './demo/components/notfound/notfound.component';
import { AppLayoutComponent } from "./layout/app.layout.component";
import { AuthGuard } from './demo/guards/auth.guard';
import { UmsPermissionGuard } from './demo/guards/ums-permission.guard';
import { DelayedPreloadingStrategy } from './delayed-preloading.strategy';
import { validIdGuard } from './demo/guards/valid-id.guard';

@NgModule({
    imports: [
        RouterModule.forRoot([
            {
                path: '', component: AppLayoutComponent,
                canActivate: [AuthGuard],
                children: [
                    // Any-of: DASHBOARD.VIEW opens every section; a DASHBOARD_<SECTION>.VIEW code opens that section alone
                    // (the page then renders only what /dashboard/sections lists). Keep on one line — validate-menu's regex is single-line.
                    { path: '', data: { breadcrumb: 'Home', pageCodes: ['DASHBOARD', 'DASHBOARD_STUDENTS', 'DASHBOARD_FINANCE', 'DASHBOARD_APPROVALS', 'DASHBOARD_ALERTS', 'DASHBOARD_COUNTRIES', 'DASHBOARD_USERS', 'DASHBOARD_QUARTER', 'DASHBOARD_NOTICES'] }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/dashboard/dashboard.module').then(m => m.DashboardModule) },
                    { path: 'students', data: { pageCode: 'STUDENTS' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/students/students.module').then(m => m.StudentsModule) },
                    { path: 'finance', data: { pageCode: 'FINANCE' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/finance/finance.module').then(m => m.FinanceModule) },
                    { path: 'studentfinancearch', data: { pageCodes: ['FINANCE_ARCH', 'FINANCE'] }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/studentfinancearch/studentfinancearch.module').then(m => m.StudentFinanceArchModule) },
                    { path: 'student-finance-wafar', data: { pageCode: 'WAFAR' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/student-finance-wafar/student-finance-wafar.module').then(m => m.StudentFinanceWafarModule) },
                    { path: 'student-quick-edit', data: { pageCode: 'STUDENT_QUICK_EDIT' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/student-quick-edit/student-quick-edit.module').then(m => m.StudentQuickEditModule) },
                    { path: 'student-extension', data: { pageCode: 'STUDENT_EXTENSION' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/student-extension/student-extension.module').then(m => m.StudentExtensionModule) },
                    { path: 'studyuniversityfee', data: { pageCode: 'FINANCE' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/studyuniversityfee/studyuniversityfee.module').then(m => m.StudyUniversityFeeModule) },
                    { path: 'student-loan', data: { pageCode: 'FINANCE' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/student-loan/student-loan.module').then(m => m.StudentLoanModule) },
                    { path: 'student-books-allowance', data: { pageCode: 'FINANCE' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/student-books-allowance/student-books-allowance.module').then(m => m.StudentBooksAllowanceModule) },
                    { path: 'settings', data: { pageCode: 'SETTINGS' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/settings.module').then(m => m.SettingsModule) },
                    { path: 'system-admin', data: { pageCode: 'SETTINGS' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/system-administration/system-administration.module').then(m => m.SystemAdministrationModule) },
                    { path: 'student-add', data: { pageCode: 'STUDENTS', permission: 'STUDENTS.ADD' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/student-add/student-add.module').then(m => m.StudentAddModule) },
                    { path: 'student-add/edit/:id', data: { pageCode: 'STUDENTS', permission: 'STUDENTS.EDIT' }, canActivate: [validIdGuard, UmsPermissionGuard], loadChildren: () => import('./demo/components/student-add/student-add.module').then(m => m.StudentAddModule) },
                    { path: 'student-history', data: { pageCode: 'STUDENT_HISTORY' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/student-history/student-history.module').then(m => m.StudentHistoryModule) },
                    { path: 'studyuniversityfeearch', data: { pageCodes: ['FINANCE_ARCH', 'FINANCE'] }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/studyuniversityfeearch/studyuniversityfeearch.module').then(m => m.StudyUniversityFeeArchModule) },
                    // No page code: every signed-in user may open their own profile (AuthGuard on the parent
                    // does the sign-in check; the permission guard passes when a route carries no permission data).
                    { path: 'profile', data: {}, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/profile/profile.module').then(m => m.ProfileModule) },
                    { path: 'reports', data: { pageCode: 'REPORTS' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/reports/reports.module').then(m => m.ReportsModule) },
                    // MoheQuarterController accepts MOHE_QUARTER.* or SETTINGS.VIEW; STUDENTS.VIEW was never enough.
                    { path: 'operations', data: { pageCodes: ['MOHE_QUARTER', 'SETTINGS'] }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/operations/operations.module').then(m => m.OperationsModule) },
                    { path: 'student-travel-tickets', data: { pageCode: 'STUDENT_TRAVEL' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/student-travel-tickets/student-travel-tickets.module').then(m => m.StudentTravelTicketsModule) },
                    { path: 'audit', data: { pageCode: 'AUDIT' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/audit/audit-log.module').then(m => m.AuditLogModule) },
                    // No gate here: each user-administration page states its own. The access review
                    // is ACCESS_REVIEW's (UmsAccessReviewController), and UMS_ADMIN here refused its reviewers.
                    { path: 'ums-admin', loadChildren: () => import('./demo/components/ums-admin/ums-admin.module').then(m => m.UmsAdminModule) },
                    // No gate here: each approvals page states its own (R5-16). My requests is open to
                    // whoever raises requests, not only to holders of the approvals permission.
                    { path: 'approvals', loadChildren: () => import('./demo/components/approvals/approvals.module').then(m => m.ApprovalsModule) },
                    { path: 'student-info-requests', data: { pageCode: 'STUDENT_INFO_REQUESTS', breadcrumb: 'Student info requests' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/student-info-requests/student-info-requests.module').then(m => m.StudentInfoRequestsModule) },
                    { path: 'student-change-requests', data: { pageCode: 'STUDENTS', breadcrumb: 'Student Change Requests' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/student-change-requests/student-change-requests.module').then(m => m.StudentChangeRequestsModule) },
                    // STUDENTS, not STUDENT_LOGIN: the student-admin controllers are gated by STUDENT_PII, and
                    // no STUDENT_LOGIN authority exists on the server (the children already said STUDENTS).
                    { path: 'student-admin', data: { pageCode: 'STUDENTS', breadcrumb: 'Student User Management' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/student-admin/student-admin.module').then(m => m.StudentAdminModule) },
                    { path: 'wafar', data: { pageCode: 'WAFAR' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/wafar/wafar.module').then(m => m.WafarModule) },
                    { path: 'finance-received-whom', data: { pageCode: 'FINANCE' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/finance-received-whom/finance-received-whom.module').then(m => m.FinanceReceivedWhomModule) },
                    { path: 'payment-authorization', data: { pageCode: 'PAYMENT_AUTH' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/payment-authorization/payment-authorization.module').then(m => m.PaymentAuthorizationModule) },
                    { path: 'wafar-reconciliation', data: { pageCode: 'WAFAR' }, canActivate: [UmsPermissionGuard], loadComponent: () => import('./demo/components/wafar-reconciliation/wafar-reconciliation.component').then(m => m.WafarReconciliationComponent) },
                    { path: 'accounting', data: { pageCodes: ['ACCOUNTING', 'FINANCE'] }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/accounting/accounting.module').then(m => m.AccountingModule) },
                    // No gate here: each notifications page states the code its endpoints need (SEND,
                    // VIEW or SETTINGS). NOTIFICATIONS.VIEW is the history's, not the sender's or the settings'.
                    { path: 'notifications', loadChildren: () => import('./demo/components/notifications/notifications.module').then(m => m.NotificationsModule) },
                    { path: 'alert-events', data: { pageCode: 'ALERT_EVENTS' }, canActivate: [UmsPermissionGuard], loadChildren: () => import('./demo/components/alert-events/alert-events.module').then(m => m.AlertEventsModule) }
                ]
            },
            // Outside the layout (it fills the window), but still a report page: same REPORTS gate as /reports.
            { path: 'reports/pdf-preview', canActivate: [AuthGuard, UmsPermissionGuard], data: { pageCode: 'REPORTS' }, loadComponent: () => import('./demo/components/reports/report-pdf-preview/report-pdf-preview.component').then(m => m.ReportPdfPreviewComponent) },
            { path: 'auth', data: { preload: false }, loadChildren: () => import('./demo/components/auth/auth.module').then(m => m.AuthModule) },
            // Staff never open the portal, so its chunk is not preloaded for them (DelayedPreloadingStrategy).
            { path: 'student-portal', data: { preload: false }, loadChildren:() => import('./student-portal/student-portal.module').then(m => m.StudentPortalModule) },
            { path: 'notfound', component: NotfoundComponent },
            // An address nobody has is not an error the system suffered (QA C2 /
            // UI-003): a mistyped URL used to land on "something went wrong",
            // which reads as a fault and invites a support call. /auth/error
            // stays for the failures that really are ours.
            { path: '**', redirectTo: '/notfound' },
            /*
             * 'top', not 'enabled'. 'enabled' means "put the reader back where
             * they were", which is only sensible when a page is the same height
             * on the way back as it was on the way out. These pages are not:
             * every list is lazy-loaded and paginated, and the student profile
             * builds all twelve of its tab panels before any of their data has
             * arrived. The router sets the scroll offset the moment routing
             * finishes, the content then grows underneath it, and the reader
             * ends up somewhere that is neither the top nor where they left.
             *
             * anchorScrolling stays on, but note it can never fire here: the
             * app uses HashLocationStrategy, so the whole route lives in the
             * fragment and there is no second '#' left to name an anchor.
             */
        ], {
            scrollPositionRestoration: 'top', anchorScrolling: 'enabled', onSameUrlNavigation: 'reload',
            // H3 (review round 2): feature chunks are fetched in the background after sign-in,
            // so a first visit to a section no longer waits for its download.
            preloadingStrategy: DelayedPreloadingStrategy
        })
    ],
    exports: [RouterModule]
})
export class AppRoutingModule {
}
