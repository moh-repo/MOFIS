package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.ProgramCatalog;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.ProgramCatalogService;
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
@RequestMapping("/api/university-reports/program-catalog")
@PreAuthorize(MoheSecurityExpressions.REPORT_ACADEMIC)
public class ProgramCatalogController {

    @Autowired
    private ProgramCatalogService service;


    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<ProgramCatalog>> getAllPrograms(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAllPrograms(countryId, countryIds));
    }

    @GetMapping("/by-university/{universityId}")
    public ResponseEntity<List<ProgramCatalog>> getByUniversity(
            @PathVariable Long universityId,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getProgramsByUniversity(universityId, countryId));
    }

    @GetMapping("/by-degree-level/{degreeLevelId}")
    public ResponseEntity<List<ProgramCatalog>> getByDegreeLevel(
            @PathVariable Integer degreeLevelId,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getProgramsByDegreeLevel(degreeLevelId, countryId));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {
        List<ProgramCatalog> items = service.getAllPrograms(countryId, countryIds);
        Map<String, Object> jasperParams = reportParamBuilder.build("PROGRAM_CATALOG", lang);
        return reportExports.render("program-catalog", jasperParams, items, "program-catalog", disposition);
    }

}
