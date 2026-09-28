package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.DataVisualization;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.DataVisualizationService;
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
@RequestMapping("/api/custom-reports/data-visualization")
@PreAuthorize(MoheSecurityExpressions.REPORT_OTHER)
public class DataVisualizationController {

    @Autowired
    private DataVisualizationService service;


    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    // RPT-05: every endpoint takes the usual optional country narrowing and is scoped
    // to the caller's countries by the service, whether or not the page sends one.

    @GetMapping
    public ResponseEntity<List<DataVisualization>> getAllVisualizations(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAllVisualizations(countryId, countryIds));
    }

    @GetMapping("/by-chart-type/{type}")
    public ResponseEntity<List<DataVisualization>> getByChartType(
            @PathVariable String type,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getVisualizationsByChartType(type, countryId, countryIds));
    }

    @GetMapping("/by-chart-name/{name}")
    public ResponseEntity<List<DataVisualization>> getByChartName(
            @PathVariable String name,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getVisualizationsByChartName(name, countryId, countryIds));
    }

    @GetMapping("/by-category/{category}")
    public ResponseEntity<List<DataVisualization>> getByCategory(
            @PathVariable String category,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getVisualizationsByCategory(category, countryId, countryIds));
    }

    @GetMapping("/chart-names")
    public ResponseEntity<List<String>> getAllChartNames(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAllChartNames(countryId, countryIds));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {
        List<DataVisualization> items = service.getAllVisualizations(countryId, countryIds);
        Map<String, Object> jasperParams = reportParamBuilder.build("DATA_VISUALIZATION", lang);
        return reportExports.render("data-visualization", jasperParams, items, "data-visualization", disposition);
    }

}