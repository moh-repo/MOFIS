package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.FeeStructure;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.FeeStructureService;
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
@RequestMapping("/api/university-reports/fee-structure")
@PreAuthorize(MoheSecurityExpressions.REPORT_FINANCE)
public class FeeStructureController {

    @Autowired
    private FeeStructureService service;


    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<FeeStructure>> getAllFeeStructures(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAllFeeStructures(countryId, countryIds));
    }

    @GetMapping("/by-university/{universityId}")
    public ResponseEntity<List<FeeStructure>> getByUniversity(
            @PathVariable Long universityId,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getFeeStructuresByUniversity(universityId, countryId));
    }

    @GetMapping("/by-degree-level/{degreeLevelId}")
    public ResponseEntity<List<FeeStructure>> getByDegreeLevel(
            @PathVariable Integer degreeLevelId,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getFeeStructuresByDegreeLevel(degreeLevelId, countryId));
    }

    @GetMapping("/by-country/{country}")
    public ResponseEntity<List<FeeStructure>> getByCountry(
            @PathVariable String country,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getFeeStructuresByCountry(country, countryId));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {
        List<FeeStructure> items = service.getAllFeeStructures(countryId, countryIds);
        Map<String, Object> jasperParams = reportParamBuilder.build("FEE_STRUCTURE", lang);
        return reportExports.render("fee-structure", jasperParams, items, "fee-structure", disposition);
    }

}
