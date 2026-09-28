package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.PerformanceMetrics;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.PerformanceMetricsService;
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
@RequestMapping("/api/statistical-reports/performance-metrics")
@PreAuthorize(MoheSecurityExpressions.REPORT_OTHER)
public class PerformanceMetricsController {

    @Autowired
    private PerformanceMetricsService service;


    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<PerformanceMetrics>> getAllMetrics(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        if (category != null || status != null) {
            return ResponseEntity.ok(service.getFiltered(countryId, countryIds, category, status));
        }
        return ResponseEntity.ok(service.getAllMetrics(countryId, countryIds));
    }

    @GetMapping("/by-category/{category}")
    public ResponseEntity<List<PerformanceMetrics>> getByCategory(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String category) {
        return ResponseEntity.ok(service.getMetricsByCategory(countryId, category));
    }

    @GetMapping("/by-performance-status/{status}")
    public ResponseEntity<List<PerformanceMetrics>> getByPerformanceStatus(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String status) {
        return ResponseEntity.ok(service.getMetricsByPerformanceStatus(countryId, status));
    }

    @GetMapping("/by-minimum-achievement")
    public ResponseEntity<List<PerformanceMetrics>> getByMinimumAchievement(
            @RequestParam(required = false) Integer countryId,
            @RequestParam BigDecimal achievement) {
        return ResponseEntity.ok(service.getMetricsByMinimumAchievement(countryId, achievement));
    }

    @GetMapping("/by-period/{period}")
    public ResponseEntity<List<PerformanceMetrics>> getByMeasurementPeriod(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String period) {
        return ResponseEntity.ok(service.getMetricsByMeasurementPeriod(countryId, period));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getAllCategories(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getAllMetricCategories(countryId));
    }

    @GetMapping("/metric-categories")
    public ResponseEntity<List<String>> getAllMetricCategoriesAlias(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getAllMetricCategories(countryId));
    }

    @GetMapping("/performance-statuses")
    public ResponseEntity<List<String>> getPerformanceStatuses(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getAllPerformanceStatuses(countryId));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String metricCategory,
            @RequestParam(required = false) String performanceStatus,
            @RequestParam(defaultValue = "ar") String lang) {
        String effectiveCategory = category != null ? category : metricCategory;
        String effectiveStatus = status != null ? status : performanceStatus;
        List<PerformanceMetrics> items = service.getFiltered(countryId, countryIds, effectiveCategory, effectiveStatus);
        Map<String, Object> jasperParams = reportParamBuilder.build("PERFORMANCE_METRICS", lang);
        return reportExports.render("performance-metrics", jasperParams, items, "performance-metrics", disposition);
    }

}
