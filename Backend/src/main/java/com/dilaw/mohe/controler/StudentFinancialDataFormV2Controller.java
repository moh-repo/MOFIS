package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.StudentFinancialDataFormV2;
import com.dilaw.mohe.report.ReportExports;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.service.StudentFinancialDataFormV2Service;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Financial Data Form V2 — the quarterly financial assistance statement
 * ("كشوفات المساعدة المالية الربعية"), beside the first form at
 * {@code /api/reports/financial-data-form}.
 *
 * <p>The statement is per quarter, so the export requires a quarter and a
 * year; the on-screen endpoints mirror the first form's.
 */
@RestController
@RequestMapping("/api/reports/financial-data-form-v2")
@PreAuthorize(MoheSecurityExpressions.FINANCE)
public class StudentFinancialDataFormV2Controller {

    private static final String REPORT_CODE = "FINANCIAL_DATA_FORM_V2";
    private static final String TEMPLATE = "financial-data-form-v2";

    private final StudentFinancialDataFormV2Service service;
    private final ReportParamBuilder reportParamBuilder;
    private final ReportExports reportExports;

    public StudentFinancialDataFormV2Controller(StudentFinancialDataFormV2Service service,
                                                ReportParamBuilder reportParamBuilder,
                                                ReportExports reportExports) {
        this.service = service;
        this.reportParamBuilder = reportParamBuilder;
        this.reportExports = reportExports;
    }

    @GetMapping("/by-quarter-year")
    public ResponseEntity<List<StudentFinancialDataFormV2>> getByQuarterAndYear(
            @RequestParam Integer quarter,
            @RequestParam Integer year,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getByQuarterAndYear(quarter, year, countryId));
    }

    @GetMapping("/by-country-quarter-year")
    public ResponseEntity<List<StudentFinancialDataFormV2>> getByCountryQuarterAndYear(
            @RequestParam String country,
            @RequestParam Integer quarter,
            @RequestParam Integer year,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getByCountryQuarterAndYear(country, quarter, year, countryId));
    }

    @GetMapping("/metadata")
    public ResponseEntity<Map<String, Object>> getMetadata(@RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getMetadata(countryId));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) String country,
            @RequestParam Integer quarter,
            @RequestParam Integer year,
            @RequestParam(defaultValue = "ar") String lang) {

        List<StudentFinancialDataFormV2> items = (country != null && !country.isBlank())
                ? service.getByCountryQuarterAndYear(country, quarter, year, countryId)
                : service.getByQuarterAndYear(quarter, year, countryId);

        boolean rtl = !"en".equalsIgnoreCase(lang);
        Map<String, Object> params = reportParamBuilder.build(REPORT_CODE, lang);
        params.put("FILTER_YEAR", String.valueOf(year));
        params.put("FILTER_QUARTER", String.valueOf(quarter));
        params.put("QUARTER_LABEL", service.quarterLabel(quarter, rtl));
        params.put("FILTER_COUNTRY", country != null && !country.isBlank()
                ? country : StudentFinancialDataFormV2Service.singleCountryName(items));
        params.put("ISSUING_ENTITY", issuingEntity(params, rtl));
        params.put("TOTAL_AMOUNT", StudentFinancialDataFormV2Service.totalAmount(items));
        params.put("TOTAL_STUDENTS", items.size());

        String baseName = TEMPLATE + "-q" + quarter + "-" + year;
        // The template name is spelled out here (not via the constant) because
        // scripts/audit_report_data.py resolves template -> bean from the literal.
        return reportExports.render("financial-data-form-v2", params, items, baseName, disposition);
    }

    /**
     * "الجهة" in the statement head is the issuing ministry — the masthead's
     * second line — in the language of the report.
     */
    private static String issuingEntity(Map<String, Object> params, boolean rtl) {
        Object ministry = params.get(rtl ? "TITLE_02_AR" : "TITLE_02_EN");
        if (ministry instanceof String s && !s.isBlank()) {
            return s;
        }
        Object fallback = params.get("TITLE_02");
        return fallback instanceof String s ? s : "";
    }
}
