package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.TrendAnalysis;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.TrendAnalysisMetadataService;
import com.dilaw.mohe.service.TrendAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports/statistical/trend-analysis")
@PreAuthorize(MoheSecurityExpressions.REPORT_OTHER)
@RequiredArgsConstructor
public class TrendAnalysisController {

    private final TrendAnalysisService service;
    private final TrendAnalysisMetadataService metadataService;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping("/search")
    public ResponseEntity<List<TrendAnalysis>> search(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String trendType,
            @RequestParam(required = false) Integer startYear,
            @RequestParam(required = false) Integer endYear,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.search(category, trendType, startYear, endYear, countryId, countryIds));
    }

    @GetMapping("/metadata")
    public ResponseEntity<?> getMetadata(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(metadataService.getMetadata(countryId, countryIds));
    }

    @GetMapping
    public ResponseEntity<List<TrendAnalysis>> getAll(
            @RequestParam(required = false) String metricType,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.search(metricType, direction, null, null, countryId, countryIds));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String metricType,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer startYear,
            @RequestParam(required = false) Integer endYear,
            @RequestParam(defaultValue = "ar") String lang) {
        List<TrendAnalysis> items = service.search(metricType, direction, startYear, endYear, countryId, countryIds);
        Map<String, Object> jasperParams = reportParamBuilder.build("TREND_ANALYSIS", lang);
        return reportExports.render("trend-analysis", jasperParams, items, "trend-analysis", disposition);
    }

}