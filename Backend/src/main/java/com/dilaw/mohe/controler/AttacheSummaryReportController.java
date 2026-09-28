package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.AttacheSummaryRowDto;
import com.dilaw.mohe.report.ReportExports;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.service.AttacheSummaryReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** One line per attaché: students, money, banks, and its own rules. */
@PreAuthorize(MoheSecurityExpressions.REPORT_OTHER)
@RestController
@RequestMapping("/api/reports/statistical/attache-summary")
@RequiredArgsConstructor
public class AttacheSummaryReportController {

    private static final String REPORT_CODE = "ATTACHE_SUMMARY";

    private final AttacheSummaryReportService reportService;
    private final ReportParamBuilder reportParamBuilder;
    private final ReportExports reportExports;

    @GetMapping("/rows")
    public ResponseEntity<List<AttacheSummaryRowDto>> rows(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {
        return ResponseEntity.ok(reportService.rows(countryId, countryIds, lang));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {

        List<AttacheSummaryRowDto> rows = reportService.rows(countryId, countryIds, lang);

        Map<String, Object> params = reportParamBuilder.build(REPORT_CODE, lang);
        boolean rtl = !"en".equalsIgnoreCase(lang);

        long withStudents = rows.stream().filter(r -> r.getStudentCount() > 0).count();
        /*
         * Said on the page rather than left to be inferred: most attachés have no
         * students yet, so most rows are zeros. A reader who does not know that
         * would read the report as broken.
         */
        params.put("COVERAGE_NOTE", rtl
                ? withStudents + " من " + rows.size() + " ملحقية لديها طلاب مسجلون."
                : withStudents + " of " + rows.size() + " attachés currently hold students.");

        return reportExports.render("attache-summary", params, rows, "attache-summary", disposition);
    }
}
