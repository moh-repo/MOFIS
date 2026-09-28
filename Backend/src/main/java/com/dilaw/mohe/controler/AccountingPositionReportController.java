package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.AccountingPositionRowDto;
import com.dilaw.mohe.dto.AccountingReportMetadataDto;
import com.dilaw.mohe.report.ReportExports;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.service.AccountingPositionReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The accounting position report.
 *
 * <p>Gated on {@code REPORT_FINANCE} rather than {@code ACCOUNTING_VIEW}: this is
 * a report about the ledger, not a way to work on it, and the finance reporting
 * audience is the one that needs it. The underlying services still apply their
 * own country scope regardless of which gate let the caller in.
 */
@PreAuthorize(MoheSecurityExpressions.REPORT_FINANCE)
@RestController
@RequestMapping("/api/reports/financial/accounting-position")
@RequiredArgsConstructor
public class AccountingPositionReportController {

    private static final String REPORT_CODE = "ACCOUNTING_TRIAL_BALANCE";

    private final AccountingPositionReportService reportService;
    private final ReportParamBuilder reportParamBuilder;
    private final ReportExports reportExports;

    @GetMapping("/metadata")
    public ResponseEntity<AccountingReportMetadataDto> metadata(
            @RequestParam(defaultValue = "ar") String lang) {
        return ResponseEntity.ok(reportService.metadata(lang));
    }

    @GetMapping("/rows")
    public ResponseEntity<?> rows(
            @RequestParam(required = false) Integer fiscalYearId,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {
        try {
            return ResponseEntity.ok(reportService.rows(fiscalYearId, countryId, countryIds, lang));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<?> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer fiscalYearId,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {

        List<AccountingPositionRowDto> rows;
        try {
            rows = reportService.rows(fiscalYearId, countryId, countryIds, lang);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }

        Map<String, Object> params = reportParamBuilder.build(REPORT_CODE, lang);
        boolean rtl = !"en".equalsIgnoreCase(lang);

        params.put("FISCAL_YEAR_NAME", rows.isEmpty() ? "" : rows.get(0).getFiscalYearName());
        /*
         * Available totals per currency, never summed across them. Printing a
         * single grand total over USD, GBP and SAR is the mistake the Wafar
         * balance card still makes; this report states each currency separately.
         */
        Map<String, BigDecimal> perCurrency = reportService.totalsByCurrency(rows);
        params.put("CURRENCY_TOTALS", perCurrency.isEmpty()
                ? (rtl ? "لا توجد أرصدة" : "No balances")
                : perCurrency.entrySet().stream()
                        .map(e -> e.getKey() + ": " + e.getValue())
                        .collect(Collectors.joining("    ")));

        return reportExports.render("accounting-position", params, rows, "accounting-position", disposition);
    }
}
