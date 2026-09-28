package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.BudgetAnalysis;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.BudgetAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports/financial/budget-analysis")
@PreAuthorize(MoheSecurityExpressions.REPORT_FINANCE)
public class BudgetAnalysisController {

    @Autowired
    private BudgetAnalysisService service;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping({"", "/", "/search"})
    public ResponseEntity<List<BudgetAnalysis>> getAll(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        if (category != null || year != null || quarter != null) {
            return ResponseEntity.ok(service.getByFilters(countryId, countryIds, category, year, quarter));
        }
        return ResponseEntity.ok(service.getAll(countryId, countryIds));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(defaultValue = "ar") String lang) {
        List<BudgetAnalysis> items = service.getByFilters(countryId, countryIds, category, year, quarter);
        Map<String, Object> jasperParams = reportParamBuilder.build("BUDGET_ANALYSIS", lang);
        return reportExports.render("budget-analysis", jasperParams, items, "budget-analysis", disposition);
    }

    @GetMapping("/metadata")
    public ResponseEntity<Map<String, Object>> getMetadata(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getMetadata(countryId, countryIds));
    }
}
