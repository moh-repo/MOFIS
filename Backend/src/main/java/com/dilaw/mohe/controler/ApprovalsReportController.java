package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.ApprovalsReportMetadataDto;
import com.dilaw.mohe.dto.ApprovalsReportRowDto;
import com.dilaw.mohe.report.ReportExports;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.service.ApprovalsReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The approvals queue report. */
@PreAuthorize(MoheSecurityExpressions.APPROVALS)
@RestController
@RequestMapping("/api/reports/operations/approvals")
@RequiredArgsConstructor
public class ApprovalsReportController {

    private static final String REPORT_CODE = "APPROVALS_REQUESTS";

    private final ApprovalsReportService reportService;
    private final ReportParamBuilder reportParamBuilder;
    private final ReportExports reportExports;

    @GetMapping("/metadata")
    public ResponseEntity<ApprovalsReportMetadataDto> metadata(
            @RequestParam(defaultValue = "ar") String lang) {
        return ResponseEntity.ok(reportService.metadata(lang));
    }

    @GetMapping("/rows")
    public ResponseEntity<List<ApprovalsReportRowDto>> rows(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {
        return ResponseEntity.ok(reportService.rows(state, countryId, countryIds, lang));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {

        List<ApprovalsReportRowDto> rows = reportService.rows(state, countryId, countryIds, lang);

        Map<String, Object> params = reportParamBuilder.build(REPORT_CODE, lang);
        boolean rtl = !"en".equalsIgnoreCase(lang);

        Map<String, Long> byStatus = reportService.countsByStatus(rows);
        params.put("STATUS_TOTALS", byStatus.isEmpty()
                ? (rtl ? "لا توجد طلبات" : "No requests")
                : byStatus.entrySet().stream()
                        .map(e -> e.getKey() + ": " + e.getValue())
                        .collect(Collectors.joining("    ")));

        // The oldest item is the one the queue is actually judged on.
        params.put("OLDEST_NOTE", rows.isEmpty()
                ? ""
                : (rtl ? "أقدم طلب: " : "Oldest request: ") + rows.get(0).getAgeDays()
                        + (rtl ? " يوم" : " days"));

        return reportExports.render("approvals-requests", params, rows, "approvals-requests", disposition);
    }
}
