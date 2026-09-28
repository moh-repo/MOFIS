package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.DistributionStatistics;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.DistributionStatisticsService;
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
@RequestMapping("/api/statistical-reports/distribution-statistics")
@PreAuthorize(MoheSecurityExpressions.REPORT_OTHER)
public class DistributionStatisticsController {

    @Autowired
    private DistributionStatisticsService service;


    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<DistributionStatistics>> getAllDistributions(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAllDistributions(countryId, countryIds));
    }

    @GetMapping("/by-type/{type}")
    public ResponseEntity<List<DistributionStatistics>> getByType(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @PathVariable String type) {
        return ResponseEntity.ok(service.getDistributionsByType(countryId, countryIds, type));
    }

    @GetMapping("/by-significance/{significance}")
    public ResponseEntity<List<DistributionStatistics>> getBySignificance(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String significance) {
        return ResponseEntity.ok(service.getDistributionsBySignificance(countryId, significance));
    }

    @GetMapping("/by-minimum-percentage")
    public ResponseEntity<List<DistributionStatistics>> getByMinimumPercentage(
            @RequestParam(required = false) Integer countryId,
            @RequestParam BigDecimal percentage) {
        return ResponseEntity.ok(service.getDistributionsByMinimumPercentage(countryId, percentage));
    }

    @GetMapping("/top-ranked")
    public ResponseEntity<List<DistributionStatistics>> getTopRanked(
            @RequestParam(required = false) Integer countryId,
            @RequestParam Long limit) {
        return ResponseEntity.ok(service.getTopRankedDistributions(countryId, limit));
    }

    @GetMapping("/types")
    public ResponseEntity<List<String>> getAllDistributionTypes(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getAllDistributionTypes(countryId));
    }

    @GetMapping("/distribution-types")
    public ResponseEntity<List<String>> getAllDistributionTypesAlias(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getAllDistributionTypes(countryId));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String distributionType,
            @RequestParam(defaultValue = "ar") String lang) {
        String effectiveType = type != null ? type : distributionType;
        List<DistributionStatistics> items = effectiveType != null
                ? service.getDistributionsByType(countryId, countryIds, effectiveType)
                : service.getAllDistributions(countryId, countryIds);
        Map<String, Object> jasperParams = reportParamBuilder.build("DISTRIBUTION_STATISTICS", lang);
        return reportExports.render("distribution-statistics", jasperParams, items, "distribution-statistics", disposition);
    }

}
