package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.ReportTemplate;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.ReportTemplateService;
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
@RequestMapping("/api/custom-reports/report-templates")
@PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
public class ReportTemplateController {

    @Autowired
    private ReportTemplateService service;


    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<ReportTemplate>> getAllTemplates() {
        return ResponseEntity.ok(service.getAllTemplates());
    }

    @GetMapping("/by-report-type/{type}")
    public ResponseEntity<List<ReportTemplate>> getByReportType(@PathVariable String type) {
        return ResponseEntity.ok(service.getTemplatesByReportType(type));
    }

    @GetMapping("/public")
    public ResponseEntity<List<ReportTemplate>> getPublicTemplates() {
        return ResponseEntity.ok(service.getPublicTemplates());
    }

    @GetMapping("/recently-used")
    public ResponseEntity<List<ReportTemplate>> getRecentlyUsed() {
        return ResponseEntity.ok(service.getRecentlyUsedTemplates());
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(defaultValue = "ar") String lang) {
        List<ReportTemplate> items = service.getAllTemplates();
        Map<String, Object> jasperParams = reportParamBuilder.build("REPORT_TEMPLATES", lang);
        return reportExports.render("report-templates", jasperParams, items, "report-templates", disposition);
    }

}