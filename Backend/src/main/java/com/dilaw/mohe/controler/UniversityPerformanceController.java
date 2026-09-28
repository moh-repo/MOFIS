package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.UniversityPerformance;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.UniversityPerformanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/university-reports/performance")
@PreAuthorize(MoheSecurityExpressions.REPORT_ACADEMIC)
public class UniversityPerformanceController {

    @Autowired
    private UniversityPerformanceService service;


    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<UniversityPerformance>> getAllPerformance(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAllPerformanceReports(countryId, countryIds));
    }

    @GetMapping("/by-retention-rate")
    public ResponseEntity<List<UniversityPerformance>> getByRetentionRate(
            @RequestParam BigDecimal rate,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getPerformanceByMinimumRetentionRate(rate, countryId));
    }

    @GetMapping("/by-country/{country}")
    public ResponseEntity<List<UniversityPerformance>> getByCountry(
            @PathVariable String country,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getPerformanceByCountry(country, countryId));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {
        List<UniversityPerformance> items = service.getAllPerformanceReports(countryId, countryIds);
        Map<String, Object> jasperParams = reportParamBuilder.build("UNIVERSITY_PERFORMANCE", lang);
        return reportExports.render("university-performance", jasperParams, items, "university-performance", disposition);
    }

}
