package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.AuditTrailReportMetadataDto;
import com.dilaw.mohe.dto.AuditTrailRowDto;
import com.dilaw.mohe.report.ReportExports;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.service.AuditTrailReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The audit trail report.
 *
 * <p>Gated on {@code AUDIT}, which resolves {@code AUDIT.VIEW} — the code the
 * audit screen already uses. Deliberately not {@code AUDIT.EXPORT}: that
 * permission exists in {@code UMSCORE.Permissions} but no code has ever checked
 * it, so gating on it would mean gating on nothing, and anyone granted it would
 * still be refused. Reviving it is a separate decision.
 *
 * <p>Unlike the other report controllers this one can refuse a request outright:
 * an absent date range, or one wide enough to exceed the row cap, is a 400 with
 * the count so the caller knows how far to narrow it.
 */
@PreAuthorize(MoheSecurityExpressions.AUDIT)
@RestController
@RequestMapping("/api/reports/operations/audit-trail")
@RequiredArgsConstructor
public class AuditTrailReportController {

    private static final String REPORT_CODE = "AUDIT_TRAIL";

    private final AuditTrailReportService reportService;
    private final ReportParamBuilder reportParamBuilder;
    private final ReportExports reportExports;

    @GetMapping("/metadata")
    public ResponseEntity<AuditTrailReportMetadataDto> metadata(
            @RequestParam(defaultValue = "ar") String lang) {
        return ResponseEntity.ok(reportService.metadata(lang));
    }

    @GetMapping("/rows")
    public ResponseEntity<?> rows(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String changedBy,
            @RequestParam(required = false) String recordId,
            @RequestParam(defaultValue = "ar") String lang) {
        try {
            return ResponseEntity.ok(
                    reportService.rows(dateFrom, dateTo, tableName, operation, changedBy, recordId, lang));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** How many records the current filters cover, so the screen can warn first. */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> count(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String changedBy,
            @RequestParam(required = false) String recordId) {
        long total = reportService.count(dateFrom, dateTo, tableName, operation, changedBy, recordId);
        return ResponseEntity.ok(Map.of("count", total));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<?> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String changedBy,
            @RequestParam(required = false) String recordId,
            @RequestParam(defaultValue = "ar") String lang) {

        List<AuditTrailRowDto> rows;
        try {
            rows = reportService.rows(dateFrom, dateTo, tableName, operation, changedBy, recordId, lang);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }

        Map<String, Object> params = reportParamBuilder.build(REPORT_CODE, lang);
        boolean rtl = !"en".equalsIgnoreCase(lang);
        /*
         * The range is printed on the page. An audit document that does not say
         * what period it covers cannot be checked against anything.
         */
        params.put("RANGE_NOTE", (rtl ? "الفترة: " : "Period: ")
                + dateFrom + (rtl ? " إلى " : " to ") + dateTo
                + "  (" + reportService.spanDays(dateFrom, dateTo) + (rtl ? " يوم)" : " days)"));

        return reportExports.render("audit-trail", params, rows, "audit-trail", disposition);
    }
}
