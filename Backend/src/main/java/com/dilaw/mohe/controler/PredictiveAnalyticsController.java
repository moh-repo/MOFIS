package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.PredictiveAnalytics;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.PredictiveAnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/statistical-reports/predictive-analytics")
@PreAuthorize(MoheSecurityExpressions.REPORT_OTHER)
public class PredictiveAnalyticsController {

    @Autowired
    private PredictiveAnalyticsService service;


    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<PredictiveAnalytics>> getAllForecasts(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAllForecasts(countryId, countryIds));
    }

    @GetMapping("/by-category/{category}")
    public ResponseEntity<List<PredictiveAnalytics>> getByCategory(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String category) {
        return ResponseEntity.ok(service.getForecastsByCategory(countryId, category));
    }

    @GetMapping("/by-year/{year}")
    public ResponseEntity<List<PredictiveAnalytics>> getByYear(
            @RequestParam(required = false) Integer countryId,
            @PathVariable Integer year) {
        return ResponseEntity.ok(service.getForecastsByYear(countryId, year));
    }

    @GetMapping("/by-confidence-level/{level}")
    public ResponseEntity<List<PredictiveAnalytics>> getByConfidenceLevel(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String level) {
        return ResponseEntity.ok(service.getForecastsByConfidenceLevel(countryId, level));
    }

    @GetMapping("/by-trend/{trend}")
    public ResponseEntity<List<PredictiveAnalytics>> getByTrend(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String trend) {
        return ResponseEntity.ok(service.getForecastsByTrend(countryId, trend));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getAllCategories(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getAllForecastCategories(countryId));
    }

    @GetMapping("/forecast-categories")
    public ResponseEntity<List<String>> getAllForecastCategoriesAlias(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getAllForecastCategories(countryId));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String forecastCategory,
            @RequestParam(required = false) String trend,
            @RequestParam(required = false) String confidenceLevel,
            @RequestParam(defaultValue = "ar") String lang) {
        String effectiveCategory = category != null ? category : forecastCategory;
        List<PredictiveAnalytics> items = service.getAllForecasts(countryId, countryIds);
        if (effectiveCategory != null) {
            items = items.stream().filter(i -> effectiveCategory.equals(i.getForecastCategory())).toList();
        }
        if (confidenceLevel != null) {
            items = items.stream().filter(i -> confidenceLevel.equals(i.getConfidenceLevel())).toList();
        }
        if (trend != null) {
            items = items.stream().filter(i -> trend.equals(i.getForecastTrend())).toList();
        }
        Map<String, Object> jasperParams = reportParamBuilder.build("PREDICTIVE_ANALYTICS", lang);
        return reportExports.render("predictive-analytics", jasperParams, items, "predictive-analytics", disposition);
    }

}
