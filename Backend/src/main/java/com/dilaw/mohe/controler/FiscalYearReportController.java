package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.FiscalYearReport;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.FiscalYearReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/periodic-reports/fiscal-year")
@PreAuthorize(MoheSecurityExpressions.REPORT_FINANCE)
public class FiscalYearReportController {

    @Autowired
    private FiscalYearReportService service;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<FiscalYearReport>> getAllReports(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAllReports(countryId, countryIds));
    }

    @GetMapping("/by-year/{year}")
    public ResponseEntity<FiscalYearReport> getByYear(
            @PathVariable Integer year,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getReportByYear(year, countryId, countryIds));
    }

    @GetMapping("/by-year-range")
    public ResponseEntity<List<FiscalYearReport>> getByYearRange(
            @RequestParam Integer startYear,
            @RequestParam Integer endYear,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getReportsByYearRange(startYear, endYear, countryId, countryIds));
    }

    @GetMapping("/by-minimum-utilization-rate")
    public ResponseEntity<List<FiscalYearReport>> getByMinimumUtilizationRate(
            @RequestParam BigDecimal rate,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getReportsByMinimumUtilizationRate(rate, countryId, countryIds));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {
        List<FiscalYearReport> items = year != null
                ? java.util.List.of(service.getReportByYear(year, countryId, countryIds))
                : service.getAllReports(countryId, countryIds);
        Map<String, Object> jasperParams = reportParamBuilder.build("FISCAL_YEAR_REPORT", lang);
        return reportExports.render("fiscal-year-report", jasperParams, items, "fiscal-year-report", disposition);
    }

}
