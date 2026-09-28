package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.AnnualReport;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.AnnualReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/periodic-reports/annual")
@PreAuthorize(MoheSecurityExpressions.REPORT_ACADEMIC)
public class AnnualReportController {

    @Autowired
    private AnnualReportService service;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<AnnualReport>> getAllReports(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAllReports(countryId, countryIds));
    }

    @GetMapping("/by-year/{year}")
    public ResponseEntity<AnnualReport> getReportByYear(
            @PathVariable Integer year,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getReportByYear(year, countryId, countryIds));
    }

    @GetMapping("/by-year-range")
    public ResponseEntity<List<AnnualReport>> getReportsByYearRange(
            @RequestParam Integer startYear,
            @RequestParam Integer endYear,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getReportsByYearRange(startYear, endYear, countryId, countryIds));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {
        List<AnnualReport> items = year != null
                ? java.util.List.of(service.getReportByYear(year, countryId, countryIds))
                : service.getAllReports(countryId, countryIds);
        Map<String, Object> jasperParams = reportParamBuilder.build("ANNUAL_REPORT", lang);
        return reportExports.render("annual-report", jasperParams, items, "annual-report", disposition);
    }

}
