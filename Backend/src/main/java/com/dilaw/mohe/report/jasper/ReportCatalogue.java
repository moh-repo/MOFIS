package com.dilaw.mohe.report.jasper;

import java.util.Set;
import java.util.TreeSet;

/**
 * The report codes the server renders: the {@code reportCode} each controller
 * hands {@link ReportParamBuilder#build}, which looks up the header, footer and
 * signatures of that code in {@code REPORT_TYPE_SETTINGS}.
 *
 * <p>Report Settings' "Add type" took any text as a code (review M3, DEC-22), so a
 * settings row could be made that no report would ever read, while a real code
 * could be misspelt. The page now picks from this list. {@code ReportCatalogueTest}
 * keeps it equal to the codes the controllers pass.
 */
public final class ReportCatalogue {

    private ReportCatalogue() {
    }

    public static final Set<String> CODES = Set.of(
            "ACADEMIC_PERFORMANCE", "ACADEMIC_YEAR_REPORT", "ACCOUNTING_TRIAL_BALANCE", "ANNUAL_REPORT",
            "APPROVALS_REQUESTS", "ATTACHE_SUMMARY", "AUDIT_TRAIL", "BANK_TRANSACTION", "BUDGET_ANALYSIS",
            "CHART_REPORT", "COMPARATIVE_STATISTICS", "CUSTOM_FILTER_REPORT", "CUSTOM_PERIOD_REPORT",
            "DATA_VISUALIZATION", "DISTRIBUTION_STATISTICS", "ENROLLMENT_CERTIFICATE",
            "ENROLLMENT_STATISTICS", "EXPORT_MANAGER", "FEE_STRUCTURE", "FINANCIAL_AUDIT",
            "FINANCIAL_DATA_FORM", "FINANCIAL_DATA_FORM_V2", "FISCAL_YEAR_REPORT", "GROWTH_ANALYSIS",
            "INDIVIDUAL_STUDENT", "MAJOR_DISTRIBUTION", "MONTHLY_REPORT", "PAYMENT_AUTH",
            "PAYMENT_AUTH_FORM", "PAYMENT_SUMMARY", "PERFORMANCE_METRICS", "PREDICTIVE_ANALYTICS",
            "PROGRAM_CATALOG", "QUARTERLY_FINANCIAL", "QUARTERLY_REPORT", "QUERY_BUILDER",
            "REPORT_TEMPLATES", "SCHEDULED_REPORTS", "SCHOLARSHIP_TRACKING", "STUDENT_DEMOGRAPHICS",
            "STUDENT_DIST_UNIVERSITY", "STUDENT_ENTITLEMENTS", "STUDENT_EXTENSION",
            "STUDENT_FINANCIAL_STATEMENT", "STUDENT_ID_CARD", "STUDENT_LIST", "STUDENT_REGISTRATION_FORM",
            "STUDENT_STATUS", "TREND_ANALYSIS", "UNIVERSITY_AGREEMENTS", "UNIVERSITY_PARTNERSHIPS",
            "UNIVERSITY_PERFORMANCE", "WAFAR_STATEMENT");

    /** The codes in order, for a picker. */
    public static TreeSet<String> sorted() {
        return new TreeSet<>(CODES);
    }
}
