package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.ScheduledReport;
import com.dilaw.mohe.model.ScheduledReportEntity;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.ScheduledReportExecutionService;
import com.dilaw.mohe.service.ScheduledReportService;
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
@RequestMapping("/api/custom-reports/scheduled-reports")
@PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
public class ScheduledReportController {

    @Autowired
    private ScheduledReportService service;

    @Autowired
    private ScheduledReportExecutionService executionService;



    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<ScheduledReport>> getAllScheduledReports() {
        return ResponseEntity.ok(service.getAllScheduledReports());
    }

    @GetMapping("/by-status/{status}")
    public ResponseEntity<List<ScheduledReport>> getByStatus(@PathVariable String status) {
        return ResponseEntity.ok(service.getScheduledReportsByStatus(status));
    }

    @GetMapping("/enabled")
    public ResponseEntity<List<ScheduledReport>> getEnabledSchedules() {
        return ResponseEntity.ok(service.getEnabledSchedules());
    }

    @GetMapping("/by-frequency/{frequency}")
    public ResponseEntity<List<ScheduledReport>> getByFrequency(@PathVariable String frequency) {
        return ResponseEntity.ok(service.getScheduledReportsByFrequency(frequency));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "ar") String lang) {
        List<ScheduledReport> items = status != null ? service.getScheduledReportsByStatus(status) : service.getAllScheduledReports();
        Map<String, Object> jasperParams = reportParamBuilder.build("SCHEDULED_REPORTS", lang);
        return reportExports.render("scheduled-reports", jasperParams, items, "scheduled-reports", disposition);
    }

    @GetMapping("/manage")
    public ResponseEntity<List<ScheduledReportEntity>> getAllManaged() {
        return ResponseEntity.ok(executionService.getAll());
    }

    @GetMapping("/manage/{id}")
    public ResponseEntity<ScheduledReportEntity> getManagedById(@PathVariable Integer id) {
        return ResponseEntity.ok(executionService.getById(id));
    }

    @PostMapping("/manage")
    public ResponseEntity<ScheduledReportEntity> createManaged(@RequestBody ScheduledReportEntity entity) {
        return ResponseEntity.ok(executionService.create(entity));
    }

    @PutMapping("/manage/{id}")
    public ResponseEntity<ScheduledReportEntity> updateManaged(@PathVariable Integer id, @RequestBody ScheduledReportEntity entity) {
        return ResponseEntity.ok(executionService.update(id, entity));
    }

    @DeleteMapping("/manage/{id}")
    public ResponseEntity<Void> deleteManaged(@PathVariable Integer id) {
        executionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/manage/{id}/toggle")
    public ResponseEntity<ScheduledReportEntity> toggleEnabled(@PathVariable Integer id) {
        return ResponseEntity.ok(executionService.toggleEnabled(id));
    }
}