package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.WafarReportMetadataDto;
import com.dilaw.mohe.dto.WafarStatementRowDto;
import com.dilaw.mohe.dto.WafarStatementSummaryDto;
import com.dilaw.mohe.report.ReportExports;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.service.WafarStatementReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The Wafar statement.
 *
 * <p>Gated on {@code REPORT_FINANCE} like the other financial reports. The export
 * method answers to all five aliases so {@link ReportExports} can pick PDF or
 * Excel from the path — the same JRXML fills both, which is what stops the
 * spreadsheet drifting from the document the user was just looking at.
 */
@PreAuthorize(MoheSecurityExpressions.REPORT_FINANCE)
@RestController
@RequestMapping("/api/reports/financial/wafar")
@RequiredArgsConstructor
public class WafarStatementReportController {

    private static final String REPORT_CODE = "WAFAR_STATEMENT";

    private final WafarStatementReportService reportService;
    private final ReportParamBuilder reportParamBuilder;
    private final ReportExports reportExports;

    @GetMapping("/metadata")
    public ResponseEntity<WafarReportMetadataDto> metadata(
            @RequestParam(defaultValue = "ar") String lang) {
        return ResponseEntity.ok(reportService.metadata(lang));
    }

    @GetMapping("/rows")
    public ResponseEntity<List<WafarStatementRowDto>> rows(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "ar") String lang) {
        return ResponseEntity.ok(
                reportService.rows(countryId, countryIds, categoryId, status, dateFrom, dateTo, lang));
    }

    @GetMapping("/summary")
    public ResponseEntity<WafarStatementSummaryDto> summary(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "ar") String lang) {
        return ResponseEntity.ok(
                reportService.summary(countryId, countryIds, categoryId, status, dateFrom, dateTo, lang));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "ar") String lang) {

        List<WafarStatementRowDto> rows =
                reportService.rows(countryId, countryIds, categoryId, status, dateFrom, dateTo, lang);
        WafarStatementSummaryDto summary =
                reportService.summary(countryId, countryIds, categoryId, status, dateFrom, dateTo, lang);

        Map<String, Object> params = reportParamBuilder.build(REPORT_CODE, lang);
        boolean rtl = !"en".equalsIgnoreCase(lang);
        params.put("TOTAL_DEPOSITS", nullSafe(summary.getTotalDeposits()));
        params.put("TOTAL_SPENT", nullSafe(summary.getTotalSpent()));
        params.put("CURRENT_BALANCE", nullSafe(summary.getCurrentBalance()));
        params.put("PENDING_AMOUNT", nullSafe(summary.getPendingAmount()));
        /*
         * Stated on the page, not left for the reader to assume: the balance
         * service sums USD, GBP and SAR as one unit. Printing a mixed-currency
         * total without saying so is the part that would mislead.
         */
        params.put("CURRENCY_NOTE", rtl
                ? "المبالغ مجمّعة عبر العملات دون تحويل."
                : "Amounts are summed across currencies without conversion.");

        return reportExports.render("wafar-statement", params, rows, "wafar-statement", disposition);
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
