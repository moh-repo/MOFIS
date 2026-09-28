package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.GrowthAnalysis;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.GrowthAnalysisService;
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
@RequestMapping("/api/statistical-reports/growth-analysis")
@PreAuthorize(MoheSecurityExpressions.REPORT_OTHER)
public class GrowthAnalysisController {

    @Autowired
    private GrowthAnalysisService service;


    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<GrowthAnalysis>> getAllGrowthAnalysis(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAllGrowthAnalysis(countryId, countryIds));
    }

    @GetMapping("/by-category/{category}")
    public ResponseEntity<List<GrowthAnalysis>> getByCategory(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String category) {
        return ResponseEntity.ok(service.getGrowthByCategory(countryId, category));
    }

    @GetMapping("/by-status/{status}")
    public ResponseEntity<List<GrowthAnalysis>> getByStatus(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String status) {
        return ResponseEntity.ok(service.getGrowthByStatus(countryId, status));
    }

    @GetMapping("/by-year-range")
    public ResponseEntity<List<GrowthAnalysis>> getByYearRange(
            @RequestParam(required = false) Integer countryId,
            @RequestParam Integer startYear,
            @RequestParam Integer endYear) {
        return ResponseEntity.ok(service.getGrowthByYearRange(countryId, startYear, endYear));
    }

    @GetMapping("/by-minimum-rate")
    public ResponseEntity<List<GrowthAnalysis>> getByMinimumRate(
            @RequestParam(required = false) Integer countryId,
            @RequestParam BigDecimal rate) {
        return ResponseEntity.ok(service.getGrowthByMinimumRate(countryId, rate));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getAllCategories(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getAllGrowthCategories(countryId));
    }

    @GetMapping("/growth-categories")
    public ResponseEntity<List<String>> getAllGrowthCategoriesAlias(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getAllGrowthCategories(countryId));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String growthCategory,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String growthStatus,
            @RequestParam(defaultValue = "ar") String lang) {
        String effectiveCategory = category != null ? category : growthCategory;
        String effectiveStatus = status != null ? status : growthStatus;
        List<GrowthAnalysis> items = service.getAllGrowthAnalysis(countryId, countryIds);
        if (effectiveCategory != null) {
            items = items.stream().filter(i -> effectiveCategory.equals(i.getGrowthCategory())).toList();
        }
        if (effectiveStatus != null) {
            items = items.stream().filter(i -> effectiveStatus.equals(i.getGrowthStatus())).toList();
        }
        Map<String, Object> jasperParams = reportParamBuilder.build("GROWTH_ANALYSIS", lang);
        // The rows go out with the category and the status named, not as the stored codes (P5-10).
        return reportExports.render("growth-analysis", jasperParams, service.reportRows(items, lang),
                "growth-analysis", disposition);
    }

}