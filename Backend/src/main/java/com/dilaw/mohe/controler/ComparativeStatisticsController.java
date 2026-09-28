package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.ComparativeStatistics;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.ComparativeStatisticsMetadataService;
import com.dilaw.mohe.service.ComparativeStatisticsService;
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
@RequestMapping("/api/reports/statistical/comparative-statistics")
@PreAuthorize(MoheSecurityExpressions.REPORT_OTHER)
@RequiredArgsConstructor
public class ComparativeStatisticsController {

    private final ComparativeStatisticsService service;
    private final ComparativeStatisticsMetadataService metadataService;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping("/search")
    public ResponseEntity<List<ComparativeStatistics>> search(
            @RequestParam(required = false) String comparisonType,
            @RequestParam(required = false) String currentPeriod,
            @RequestParam(required = false) String previousPeriod,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.search(comparisonType, currentPeriod, previousPeriod, countryId, countryIds));
    }

    @GetMapping("/metadata")
    public ResponseEntity<?> metadata(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(metadataService.getMetadata(countryId, countryIds));
    }

    @GetMapping
    public ResponseEntity<List<ComparativeStatistics>> getAll(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAll(countryId, countryIds));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String comparisonType,
            @RequestParam(required = false) String currentPeriod,
            @RequestParam(required = false) String previousPeriod,
            @RequestParam(defaultValue = "ar") String lang) {
        List<ComparativeStatistics> items = service.search(comparisonType, currentPeriod, previousPeriod, countryId, countryIds);
        Map<String, Object> jasperParams = reportParamBuilder.build("COMPARATIVE_STATISTICS", lang);
        return reportExports.render("comparative-statistics", jasperParams, items, "comparative-statistics", disposition);
    }

}