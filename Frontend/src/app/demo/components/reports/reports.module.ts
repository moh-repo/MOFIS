import { NgModule } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { ReportsRoutingModule } from './reports-routing.module';
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
import { WafarStatementComponent } from './financial-reports/wafar-statement/wafar-statement.component';
import { OperationsReportsComponent } from './operations-reports/operations-reports.component';
import { AuditTrailReportComponent } from './operations-reports/audit-trail/audit-trail.component';
import { ApprovalsReportComponent } from './operations-reports/approvals/approvals.component';
import { AccountingPositionComponent } from './financial-reports/accounting-position/accounting-position.component';
import { StudentEntitlementsComponent } from './financial-reports/student-entitlements/student-entitlements.component';
import { AttacheSummaryComponent } from './statistical-reports/attache-summary/attache-summary.component';
import { FinancialAuditComponent } from './financial-reports/financial-audit/financial-audit.component';

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
import { StudentGraphComponent } from './student-graph/student-graph.component';

// PrimeNG Modules - Essential ones only
import { ButtonModule } from 'primeng/button';
import { RippleModule } from 'primeng/ripple';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { ToastModule } from 'primeng/toast';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { TagModule } from 'primeng/tag';
import { CheckboxModule } from 'primeng/checkbox';
import { DividerModule } from 'primeng/divider';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DialogModule } from 'primeng/dialog';
import { AccordionModule } from 'primeng/accordion';
import { TabsModule } from 'primeng/tabs';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TooltipModule } from 'primeng/tooltip';
import { ProgressBarModule } from 'primeng/progressbar';
import { CardModule } from 'primeng/card';
import { PanelModule } from 'primeng/panel';
import { SplitterModule } from 'primeng/splitter';
import { ScrollPanelModule } from 'primeng/scrollpanel';
import { ChipModule } from 'primeng/chip';
import { BadgeModule } from 'primeng/badge';
import { AvatarModule } from 'primeng/avatar';
import { SkeletonModule } from 'primeng/skeleton';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { BlockUIModule } from 'primeng/blockui';
import { TerminalModule } from 'primeng/terminal';
import { PaginatorModule } from 'primeng/paginator';
import { MultiSelectModule } from 'primeng/multiselect';
import { ListboxModule } from 'primeng/listbox';
import { RadioButtonModule } from 'primeng/radiobutton';
import { SliderModule } from 'primeng/slider';
import { RatingModule } from 'primeng/rating';
import { ToggleButtonModule } from 'primeng/togglebutton';
import { ColorPickerModule } from 'primeng/colorpicker';
import { CascadeSelectModule } from 'primeng/cascadeselect';
import { EditorModule } from 'primeng/editor';
import { FileUploadModule } from 'primeng/fileupload';
import { GalleriaModule } from 'primeng/galleria';
import { ImageModule } from 'primeng/image';
import { CarouselModule } from 'primeng/carousel';
import { DataViewModule } from 'primeng/dataview';
import { OrderListModule } from 'primeng/orderlist';
import { PickListModule } from 'primeng/picklist';
import { TreeModule } from 'primeng/tree';
import { TreeTableModule } from 'primeng/treetable';
import { OrganizationChartModule } from 'primeng/organizationchart';
import { TimelineModule } from 'primeng/timeline';
import { ChartModule } from 'primeng/chart';
import { MenuModule } from 'primeng/menu';
import { MenubarModule } from 'primeng/menubar';
import { PanelMenuModule } from 'primeng/panelmenu';
import { StepsModule } from 'primeng/steps';
import { TieredMenuModule } from 'primeng/tieredmenu';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ContextMenuModule } from 'primeng/contextmenu';
import { DockModule } from 'primeng/dock';
import { MegaMenuModule } from 'primeng/megamenu';
import { SpeedDialModule } from 'primeng/speeddial';
import { SplitButtonModule } from 'primeng/splitbutton';
import { ToolbarModule } from 'primeng/toolbar';
import { ConfirmPopupModule } from 'primeng/confirmpopup';
import { PopoverModule } from 'primeng/popover';
import { DrawerModule } from 'primeng/drawer';
import { FocusTrapModule } from 'primeng/focustrap';
import { StyleClassModule } from 'primeng/styleclass';
import { AnimateOnScrollModule } from 'primeng/animateonscroll';
import { SharedModule } from '../../shared/shared.module';
import { HasPermissionDirective } from '../../directives/has-permission.directive';
import { AgGridModule } from 'ag-grid-angular';
import { NgxEchartsModule } from 'ngx-echarts';

@NgModule({ declarations: [
        ReportsListComponent,
        GraphsComponent,
        BarChartsComponent,
        LineChartsComponent,
        PieChartsComponent,
        HierarchicalChartsComponent,
        GeographicMapsComponent,
        ComprehensiveDashboardsComponent,
        FinancialDataFormComponent,
        FinancialDataFormV2Component,
        QuarterlyFinancialReportComponent,
        StudentReportsComponent,
        FinancialReportsComponent,
        StatisticalReportsComponent,
        PeriodicReportsComponent,
        UniversityReportsComponent,
        CustomReportsComponent,
        // Student Report Components
        EnrollmentStatisticsComponent,
        StudentDemographicsComponent,
        AcademicPerformanceComponent,
        StudentStatusReportComponent,
        MajorDistributionComponent,
        StudentListDetailedComponent,
        IndividualStudentReportsComponent,
        // Financial Report Components
        PaymentAuthReportComponent,
        PaymentSummaryComponent,
        ScholarshipTrackingComponent,
        BudgetAnalysisComponent,
        BankTransactionComponent,
        FinancialAuditComponent,
        WafarStatementComponent,
        AccountingPositionComponent,
        StudentEntitlementsComponent,
        // Operations Report Components
        OperationsReportsComponent,
        AuditTrailReportComponent,
        ApprovalsReportComponent,
        // Statistical Report Components
        TrendAnalysisComponent,
        ComparativeStatisticsComponent,
        PerformanceMetricsComponent,
        GrowthAnalysisComponent,
        DistributionStatisticsComponent,
        PredictiveAnalyticsComponent,
        StudentExtensionStatisticsComponent,
        AttacheSummaryComponent,
        // Periodic Report Components
        MonthlyReportComponent,
        QuarterlyReportComponent,
        AnnualReportComponent,
        AcademicYearReportComponent,
        FiscalYearReportComponent,
        CustomPeriodReportComponent,
        // University Report Components
        UniversityPartnershipsComponent,
        FeeStructureComponent,
        UniversityPerformanceComponent,
        ProgramCatalogComponent,
        StudentDistributionComponent,
        UniversityAgreementsComponent,
        // Custom Report Components
        CustomFilterReportComponent,
        QueryBuilderComponent,
        ExportManagerComponent,
        ScheduledReportsComponent,
        ReportTemplatesComponent,
        DataVisualizationComponent,
        StudentGraphComponent
    ], imports: [CommonModule,
        FormsModule,
        ReportsRoutingModule,
        ButtonModule,
        RippleModule,
        SelectModule,
        TableModule,
        ToastModule,
        MessageModule,
        TagModule,
        CheckboxModule,
        DividerModule,
        InputTextModule,
        TextareaModule,
        DatePickerModule,
        InputNumberModule,
        AutoCompleteModule,
        ConfirmDialogModule,
        DialogModule,
        AccordionModule,
        TabsModule,
        SelectButtonModule,
        TooltipModule,
        ProgressBarModule,
        CardModule,
        PanelModule,
        SplitterModule,
        ScrollPanelModule,
        ChipModule,
        BadgeModule,
        AvatarModule,
        SkeletonModule,
        ProgressSpinnerModule,
        BlockUIModule,
        TerminalModule,
        PaginatorModule,
        MultiSelectModule,
        ListboxModule,
        RadioButtonModule,
        SliderModule,
        RatingModule,
        ToggleButtonModule,
        ColorPickerModule,
        CascadeSelectModule,
        EditorModule,
        FileUploadModule,
        GalleriaModule,
        ImageModule,
        CarouselModule,
        DataViewModule,
        OrderListModule,
        PickListModule,
        TreeModule,
        TreeTableModule,
        OrganizationChartModule,
        TimelineModule,
        ChartModule,
        MenuModule,
        MenubarModule,
        PanelMenuModule,
        StepsModule,
        TieredMenuModule,
        BreadcrumbModule,
        ContextMenuModule,
        DockModule,
        MegaMenuModule,
        SpeedDialModule,
        SplitButtonModule,
        ToolbarModule,
        ConfirmPopupModule,
        PopoverModule,
        DrawerModule,
        FocusTrapModule,
        StyleClassModule,
        AnimateOnScrollModule,
        SharedModule,
        HasPermissionDirective,
        AgGridModule,
        NgxEchartsModule.forRoot({
            echarts: () => import('echarts'),
        })], providers: [
        MessageService,
        DecimalPipe,
        provideHttpClient(withInterceptorsFromDi())
    ] })
export class ReportsModule { }

