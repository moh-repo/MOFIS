package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.ExportManager;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.ExportManagerService;
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
@RequestMapping("/api/custom-reports/export-manager")
@PreAuthorize(MoheSecurityExpressions.REPORT_OTHER)
public class ExportManagerController {

    @Autowired
    private ExportManagerService service;


    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<ExportManager>> getAllExports() {
        return ResponseEntity.ok(service.getAllExports());
    }

    @GetMapping("/by-status/{status}")
    public ResponseEntity<List<ExportManager>> getByStatus(@PathVariable String status) {
        return ResponseEntity.ok(service.getExportsByStatus(status));
    }

    @GetMapping("/available")
    public ResponseEntity<List<ExportManager>> getAvailableExports() {
        return ResponseEntity.ok(service.getAvailableExports());
    }

    @GetMapping("/by-format/{format}")
    public ResponseEntity<List<ExportManager>> getByFormat(@PathVariable String format) {
        return ResponseEntity.ok(service.getExportsByFormat(format));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(defaultValue = "ar") String lang) {
        List<ExportManager> items = service.getAllExports();
        Map<String, Object> jasperParams = reportParamBuilder.build("EXPORT_MANAGER", lang);
        return reportExports.render("export-manager", jasperParams, items, "export-manager", disposition);
    }

}