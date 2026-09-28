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

    @GetMapping
    public ResponseEntity<List<DataVisualization>> getAllVisualizations() {
        return ResponseEntity.ok(service.getAllVisualizations());
    }

    @GetMapping("/by-chart-type/{type}")
    public ResponseEntity<List<DataVisualization>> getByChartType(@PathVariable String type) {
        return ResponseEntity.ok(service.getVisualizationsByChartType(type));
    }

    @GetMapping("/by-chart-name/{name}")
    public ResponseEntity<List<DataVisualization>> getByChartName(@PathVariable String name) {
        return ResponseEntity.ok(service.getVisualizationsByChartName(name));
    }

    @GetMapping("/by-category/{category}")
    public ResponseEntity<List<DataVisualization>> getByCategory(@PathVariable String category) {
        return ResponseEntity.ok(service.getVisualizationsByCategory(category));
    }

    @GetMapping("/chart-names")
    public ResponseEntity<List<String>> getAllChartNames() {
        return ResponseEntity.ok(service.getAllChartNames());
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(defaultValue = "ar") String lang) {
        List<DataVisualization> items = service.getAllVisualizations();
        Map<String, Object> jasperParams = reportParamBuilder.build("DATA_VISUALIZATION", lang);
        return reportExports.render("data-visualization", jasperParams, items, "data-visualization", disposition);
    }

}