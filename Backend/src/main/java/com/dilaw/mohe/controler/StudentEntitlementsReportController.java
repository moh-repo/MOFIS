package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.StudentEntitlementRowDto;
import com.dilaw.mohe.dto.StudentEntitlementsMetadataDto;
import com.dilaw.mohe.report.ReportExports;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.service.StudentEntitlementsReportService;
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

/** Loans, books allowance and travel tickets as one statement. */
@PreAuthorize(MoheSecurityExpressions.REPORT_FINANCE)
@RestController
@RequestMapping("/api/reports/financial/student-entitlements")
@RequiredArgsConstructor
public class StudentEntitlementsReportController {

    private static final String REPORT_CODE = "STUDENT_ENTITLEMENTS";

    private final StudentEntitlementsReportService reportService;
    private final ReportParamBuilder reportParamBuilder;
    private final ReportExports reportExports;

    @GetMapping("/metadata")
    public ResponseEntity<StudentEntitlementsMetadataDto> metadata(
            @RequestParam(defaultValue = "ar") String lang) {
        return ResponseEntity.ok(reportService.metadata(lang));
    }

    @GetMapping("/rows")
    public ResponseEntity<List<StudentEntitlementRowDto>> rows(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) Integer studentId,
            @RequestParam(defaultValue = "ar") String lang) {
        return ResponseEntity.ok(reportService.rows(countryId, countryIds, kind, studentId, lang));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) Integer studentId,
            @RequestParam(defaultValue = "ar") String lang) {

        List<StudentEntitlementRowDto> rows =
                reportService.rows(countryId, countryIds, kind, studentId, lang);

        Map<String, Object> params = reportParamBuilder.build(REPORT_CODE, lang);
        boolean rtl = !"en".equalsIgnoreCase(lang);

        Map<String, BigDecimal> perKind = reportService.totalsByKind(rows);
        /*
         * Totals per kind, not one grand total. A loan, a books allowance and a
         * ticket are different obligations in possibly different currencies;
         * adding them would produce a number that means nothing.
         */
        params.put("KIND_TOTALS", perKind.isEmpty()
                ? (rtl ? "لا توجد مستحقات" : "No entitlements")
                : perKind.entrySet().stream()
                        .map(e -> e.getKey() + ": " + e.getValue())
                        .collect(Collectors.joining("    ")));

        return reportExports.render("student-entitlements", params, rows, "student-entitlements", disposition);
    }
}
