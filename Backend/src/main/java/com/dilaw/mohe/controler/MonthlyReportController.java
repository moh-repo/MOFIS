package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.MonthlyReport;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.MonthlyReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/periodic-reports/monthly")
@PreAuthorize(MoheSecurityExpressions.REPORT_FINANCE)
public class MonthlyReportController {

    @Autowired
    private MonthlyReportService service;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<MonthlyReport>> getAllReports(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAllReports(countryId, countryIds));
    }

    @GetMapping("/by-year/{year}")
    public ResponseEntity<List<MonthlyReport>> getByYear(
            @PathVariable Integer year,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getReportsByYear(year, countryId, countryIds));
    }

    @GetMapping("/by-year-range")
    public ResponseEntity<List<MonthlyReport>> getByYearRange(
            @RequestParam Integer startYear,
            @RequestParam Integer endYear,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getReportsByYearRange(startYear, endYear, countryId, countryIds));
    }

    @GetMapping("/by-year-month")
    public ResponseEntity<MonthlyReport> getByYearAndMonth(
            @RequestParam Integer year,
            @RequestParam Integer month,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getReportByYearAndMonth(year, month, countryId, countryIds));
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
        List<MonthlyReport> items = year != null
                ? service.getReportsByYear(year, countryId, countryIds)
                : service.getAllReports(countryId, countryIds);
        Map<String, Object> jasperParams = reportParamBuilder.build("MONTHLY_REPORT", lang);
        return reportExports.render("monthly-report", jasperParams, items, "monthly-report", disposition);
    }

}
