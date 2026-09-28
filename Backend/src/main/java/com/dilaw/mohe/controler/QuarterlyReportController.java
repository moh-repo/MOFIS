package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.QuarterlyReport;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.QuarterlyReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/periodic-reports/quarterly")
@PreAuthorize(MoheSecurityExpressions.REPORT_FINANCE)
public class QuarterlyReportController {

    @Autowired
    private QuarterlyReportService service;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<QuarterlyReport>> getAllReports(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAllReports(countryId, countryIds));
    }

    @GetMapping("/by-year/{year}")
    public ResponseEntity<List<QuarterlyReport>> getByYear(
            @PathVariable Integer year,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getReportsByYear(year, countryId, countryIds));
    }

    @GetMapping("/by-year-range")
    public ResponseEntity<List<QuarterlyReport>> getByYearRange(
            @RequestParam Integer startYear,
            @RequestParam Integer endYear,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getReportsByYearRange(startYear, endYear, countryId, countryIds));
    }

    @GetMapping("/by-year-quarter")
    public ResponseEntity<QuarterlyReport> getByYearAndQuarter(
            @RequestParam Integer year,
            @RequestParam Integer quarter,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getReportByYearAndQuarter(year, quarter, countryId, countryIds));
    }

    @GetMapping("/years")
    public ResponseEntity<List<Integer>> getAllYears(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAllYears(countryId, countryIds));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {
        List<QuarterlyReport> items = year != null
                ? service.getReportsByYear(year, countryId, countryIds)
                : service.getAllReports(countryId, countryIds);
        Map<String, Object> jasperParams = reportParamBuilder.build("QUARTERLY_REPORT", lang);
        return reportExports.render("quarterly-report", jasperParams, items, "quarterly-report", disposition);
    }

}
