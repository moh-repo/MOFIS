import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UmsPermissionGuard } from '../../guards/ums-permission.guard';
import { ReportsListComponent } from './reports-list/reports-list.component';
import { GraphsComponent } from './graphs/graphs.component';
import { BarChartsComponent } from './bar-charts/bar-charts.component';
import { LineChartsComponent } from './line-charts/line-charts.component';
import { PieChartsComponent } from './pie-charts/pie-charts.component';
import { HierarchicalChartsComponent } from './hierarchical-charts/hierarchical-charts.component';
import { GeographicMapsComponent } from './geographic-maps/geographic-maps.component';
import { ComprehensiveDashboardsComponent } from './comprehensive-dashboards/comprehensive-dashboards.component';
import { FinancialDataFormComponent } from './financial-data-form/financial-data-form.component';
import { FinancialDataFormV2Component } from './financial-data-form-v2/financial-data-form-v2.component';
import { QuarterlyFinancialReportComponent } from './quarterly-financial-report/quarterly-financial-report.component';
import { StudentReportsComponent } from './student-reports/student-reports.component';
import { FinancialReportsComponent } from './financial-reports/financial-reports.component';
import { StatisticalReportsComponent } from './statistical-reports/statistical-reports.component';
import { PeriodicReportsComponent } from './periodic-reports/periodic-reports.component';
import { UniversityReportsComponent } from './university-reports/university-reports.component';
import { CustomReportsComponent } from './custom-reports/custom-reports.component';
import { StudentGraphComponent } from './student-graph/student-graph.component';

// Student Report Components
import { EnrollmentStatisticsComponent } from './student-reports/enrollment-statistics/enrollment-statistics.component';
import { StudentDemographicsComponent } from './student-reports/student-demographics/student-demographics.component';
import { AcademicPerformanceComponent } from './student-reports/academic-performance/academic-performance.component';
import { StudentStatusReportComponent } from './student-reports/student-status-report/student-status-report.component';
import { MajorDistributionComponent } from './student-reports/major-distribution/major-distribution.component';
import { StudentListDetailedComponent } from './student-reports/student-list-detailed/student-list-detailed.component';
import { IndividualStudentReportsComponent } from './student-reports/individual-student-reports/individual-student-reports.component';

// Financial Report Components
import { PaymentAuthReportComponent } from './financial-reports/payment-auth-report/payment-auth-report.component';
import { PaymentSummaryComponent } from './financial-reports/payment-summary/payment-summary.component';
import { ScholarshipTrackingComponent } from './financial-reports/scholarship-tracking/scholarship-tracking.component';
import { BudgetAnalysisComponent } from './financial-reports/budget-analysis/budget-analysis.component';
import { BankTransactionComponent } from './financial-reports/bank-transaction/bank-transaction.component';
import { FinancialAuditComponent } from './financial-reports/financial-audit/financial-audit.component';
import { WafarStatementComponent } from './financial-reports/wafar-statement/wafar-statement.component';
import { OperationsReportsComponent } from './operations-reports/operations-reports.component';
import { AuditTrailReportComponent } from './operations-reports/audit-trail/audit-trail.component';
import { ApprovalsReportComponent } from './operations-reports/approvals/approvals.component';
import { AccountingPositionComponent } from './financial-reports/accounting-position/accounting-position.component';
import { StudentEntitlementsComponent } from './financial-reports/student-entitlements/student-entitlements.component';
import { AttacheSummaryComponent } from './statistical-reports/attache-summary/attache-summary.component';

// Statistical Report Components
import { TrendAnalysisComponent } from './statistical-reports/trend-analysis/trend-analysis.component';
import { ComparativeStatisticsComponent } from './statistical-reports/comparative-statistics/comparative-statistics.component';
import { PerformanceMetricsComponent } from './statistical-reports/performance-metrics/performance-metrics.component';
import { GrowthAnalysisComponent } from './statistical-reports/growth-analysis/growth-analysis.component';
import { DistributionStatisticsComponent } from './statistical-reports/distribution-statistics/distribution-statistics.component';
import { PredictiveAnalyticsComponent } from './statistical-reports/predictive-analytics/predictive-analytics.component';
import { StudentExtensionStatisticsComponent } from './statistical-reports/student-extension-statistics/student-extension-statistics.component';

// Periodic Report Components
import { MonthlyReportComponent } from './periodic-reports/monthly-report/monthly-report.component';
import { QuarterlyReportComponent } from './periodic-reports/quarterly-report/quarterly-report.component';
import { AnnualReportComponent } from './periodic-reports/annual-report/annual-report.component';
import { AcademicYearReportComponent } from './periodic-reports/academic-year-report/academic-year-report.component';
import { FiscalYearReportComponent } from './periodic-reports/fiscal-year-report/fiscal-year-report.component';
import { CustomPeriodReportComponent } from './periodic-reports/custom-period-report/custom-period-report.component';

// University Report Components
import { UniversityPartnershipsComponent } from './university-reports/university-partnerships/university-partnerships.component';
import { FeeStructureComponent } from './university-reports/fee-structure/fee-structure.component';
import { UniversityPerformanceComponent } from './university-reports/university-performance/university-performance.component';
import { ProgramCatalogComponent } from './university-reports/program-catalog/program-catalog.component';
import { StudentDistributionComponent } from './university-reports/student-distribution/student-distribution.component';
import { UniversityAgreementsComponent } from './university-reports/university-agreements/university-agreements.component';

// Custom Report Components
import { CustomFilterReportComponent } from './custom-reports/custom-filter-report/custom-filter-report.component';
import { QueryBuilderComponent } from './custom-reports/query-builder/query-builder.component';
import { ExportManagerComponent } from './custom-reports/export-manager/export-manager.component';
import { ScheduledReportsComponent } from './custom-reports/scheduled-reports/scheduled-reports.component';
import { ReportTemplatesComponent } from './custom-reports/report-templates/report-templates.component';
import { DataVisualizationComponent } from './custom-reports/data-visualization/data-visualization.component';

const routes: Routes = [
    {
        path: '',
        children: [
            // General report pages — guarded by parent REPORTS.VIEW (from app-routing.module.ts)
            { path: 'list', component: ReportsListComponent },
            { path: 'graphs', component: GraphsComponent },
            { path: 'geographic-maps', component: GeographicMapsComponent },

            /*
             * Chart pages. Their data comes from ChartDataController (REPORT_OTHER) and
             * StudentGraphController (REPORT_ACADEMIC), so the guard asks for the same
             * code the API will: without it a REPORTS.VIEW-only user reached the page and
             * got a silently swallowed 403 — an empty chart with no explanation.
             */
            { path: 'student-graph', component: StudentGraphComponent, canActivate: [UmsPermissionGuard], data: { pageCode: 'REPORT_ACADEMIC' } },
            { path: 'bar-charts', component: BarChartsComponent, canActivate: [UmsPermissionGuard], data: { pageCode: 'REPORT_OTHER' } },
            { path: 'line-charts', component: LineChartsComponent, canActivate: [UmsPermissionGuard], data: { pageCode: 'REPORT_OTHER' } },
            { path: 'pie-charts', component: PieChartsComponent, canActivate: [UmsPermissionGuard], data: { pageCode: 'REPORT_OTHER' } },
            { path: 'hierarchical-charts', component: HierarchicalChartsComponent, canActivate: [UmsPermissionGuard], data: { pageCode: 'REPORT_OTHER' } },
            { path: 'comprehensive-dashboards', component: ComprehensiveDashboardsComponent, canActivate: [UmsPermissionGuard], data: { pageCode: 'REPORT_OTHER' } },
            { path: 'financial-data-form', component: FinancialDataFormComponent, canActivate: [UmsPermissionGuard], data: { pageCodes: ['REPORT_FINANCE', 'FINANCE'] } },
            { path: 'financial-data-form-v2', component: FinancialDataFormV2Component, canActivate: [UmsPermissionGuard], data: { pageCodes: ['REPORT_FINANCE', 'FINANCE'] } },
            { path: 'quarterly-financial-report', component: QuarterlyFinancialReportComponent, canActivate: [UmsPermissionGuard], data: { pageCode: 'REPORT_FINANCE' } },

            // Student Academic Reports — require REPORT_ACADEMIC.VIEW
            {
                path: 'student',
                canActivate: [UmsPermissionGuard],
                data: { pageCode: 'REPORT_ACADEMIC' },
                children: [
                    { path: '', component: StudentReportsComponent },
                    { path: 'enrollment-statistics', component: EnrollmentStatisticsComponent },
                    { path: 'demographics', component: StudentDemographicsComponent },
                    { path: 'academic-performance', component: AcademicPerformanceComponent },
                    { path: 'status-report', component: StudentStatusReportComponent },
                    { path: 'major-distribution', component: MajorDistributionComponent },
                    // Student records (live QA M6, V143): the category code alone let a report viewer in to be refused every row.
                    { path: 'list-detailed', component: StudentListDetailedComponent, canActivate: [UmsPermissionGuard], data: { pageCodes: ['STUDENTS', 'STUDENTS_LIST', 'UMS_ADMIN'] } },
                    { path: 'individual-student', component: IndividualStudentReportsComponent },
                ]
            },

            // Financial Reports — require REPORT_FINANCE.VIEW
            {
                path: 'financial',
                canActivate: [UmsPermissionGuard],
                data: { pageCode: 'REPORT_FINANCE' },
                children: [
                    { path: '', component: FinancialReportsComponent },
                    { path: 'payment-auth-report', component: PaymentAuthReportComponent },
                    { path: 'payment-summary', component: PaymentSummaryComponent },
                    { path: 'scholarship-tracking', component: ScholarshipTrackingComponent },
                    { path: 'budget-analysis', component: BudgetAnalysisComponent },
                    { path: 'bank-transaction', component: BankTransactionComponent },
                    { path: 'financial-audit', component: FinancialAuditComponent },
                    { path: 'wafar-statement', component: WafarStatementComponent },
                    { path: 'accounting-position', component: AccountingPositionComponent },
                    { path: 'student-entitlements', component: StudentEntitlementsComponent },
                ]
            },

            /*
             * Operations Reports — approvals and the audit trail, which are
             * neither money nor analytics. The audit report gates on AUDIT.VIEW
             * at the API; REPORT_OTHER here is the category gate, matching the
             * other five categories.
             */
            {
                path: 'operations',
                canActivate: [UmsPermissionGuard],
                data: { pageCode: 'REPORT_OTHER' },
                children: [
                    { path: '', component: OperationsReportsComponent },
                    // The data needs AUDIT / APPROVALS (live QA M6, V143), as the cards now say.
                    { path: 'audit-trail', component: AuditTrailReportComponent, canActivate: [UmsPermissionGuard], data: { pageCodes: ['AUDIT', 'UMS_ADMIN'] } },
                    { path: 'approvals', component: ApprovalsReportComponent, canActivate: [UmsPermissionGuard], data: { pageCodes: ['APPROVALS', 'APP_PENDING', 'UMS_ADMIN'] } },
                ]
            },

            // Statistical Reports — require REPORT_OTHER.VIEW
            {
                path: 'statistical',
                canActivate: [UmsPermissionGuard],
                data: { pageCode: 'REPORT_OTHER' },
                children: [
                    { path: '', component: StatisticalReportsComponent },
                    { path: 'trend-analysis', component: TrendAnalysisComponent },
                    { path: 'comparative-statistics', component: ComparativeStatisticsComponent },
                    { path: 'performance-metrics', component: PerformanceMetricsComponent },
                    { path: 'growth-analysis', component: GrowthAnalysisComponent },
                    { path: 'distribution-statistics', component: DistributionStatisticsComponent },
                    { path: 'predictive-analytics', component: PredictiveAnalyticsComponent },
                    { path: 'student-extension-statistics', component: StudentExtensionStatisticsComponent },
                    { path: 'attache-summary', component: AttacheSummaryComponent },
                    // Its network is student records (STUDENT_PII on /api/tracer; live QA R5-08), as the sidebar row now says (MENU_ROUTE_CODES).
                    {
                        path: 'student-tracer',
                        canActivate: [UmsPermissionGuard],
                        data: { pageCodes: ['STUDENTS', 'STUDENTS_LIST', 'STUDENTS_ADD', 'STUDENT_QUICK_EDIT', 'STUDENT_HISTORY', 'UMS_ADMIN'] },
                        loadChildren: () => import('../student-tracer/student-tracer.module').then(m => m.StudentTracerModule)
                    },
                ]
            },

            // Periodic Reports — require REPORT_OTHER.VIEW
            {
                path: 'periodic',
                canActivate: [UmsPermissionGuard],
                data: { pageCode: 'REPORT_OTHER' },
                children: [
                    { path: '', component: PeriodicReportsComponent },
                    { path: 'monthly', component: MonthlyReportComponent },
                    { path: 'quarterly', component: QuarterlyReportComponent },
                    { path: 'annual', component: AnnualReportComponent },
                    { path: 'academic-year', component: AcademicYearReportComponent },
                    { path: 'fiscal-year', component: FiscalYearReportComponent },
                    { path: 'custom-period', component: CustomPeriodReportComponent },
                ]
            },

            // University Reports — require REPORT_OTHER.VIEW
            {
                path: 'university',
                canActivate: [UmsPermissionGuard],
                data: { pageCode: 'REPORT_OTHER' },
                children: [
                    { path: '', component: UniversityReportsComponent },
                    { path: 'partnerships', component: UniversityPartnershipsComponent },
                    { path: 'fee-structure', component: FeeStructureComponent },
                    { path: 'performance', component: UniversityPerformanceComponent },
                    { path: 'program-catalog', component: ProgramCatalogComponent },
                    { path: 'student-distribution', component: StudentDistributionComponent },
                    { path: 'agreements', component: UniversityAgreementsComponent },
                ]
            },

            // Custom Reports — require REPORT_OTHER.VIEW
            {
                path: 'custom',
                canActivate: [UmsPermissionGuard],
                data: { pageCode: 'REPORT_OTHER' },
                children: [
                    { path: '', component: CustomReportsComponent },
                    { path: 'custom-filter', component: CustomFilterReportComponent },
                    { path: 'query-builder', component: QueryBuilderComponent },
                    { path: 'export-manager', component: ExportManagerComponent },
                    { path: 'scheduled-reports', component: ScheduledReportsComponent },
                    { path: 'report-templates', component: ReportTemplatesComponent },
                    { path: 'data-visualization', component: DataVisualizationComponent },
                ]
            },

            { path: '', redirectTo: 'list', pathMatch: 'full' }
        ]
    }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class ReportsRoutingModule { }
