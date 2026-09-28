package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.MajorDistribution;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.service.MajorDistributionService;
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
@RequestMapping("/api/reports/major-distribution")
@PreAuthorize(MoheSecurityExpressions.REPORT_ACADEMIC)
public class MajorDistributionController {

    @Autowired
    private MajorDistributionService service;


    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping("/search")
    public ResponseEntity<List<MajorDistribution>> getAll(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) Integer majorId,
            @RequestParam(required = false) Integer degreeId,
            @RequestParam(required = false) Integer studyTypeId) {
        return ResponseEntity.ok(service.search(countryId, countryIds, majorId, degreeId, studyTypeId));
    }

    @GetMapping("/by-count")
    public ResponseEntity<List<MajorDistribution>> getAllOrderByCount() {
        return ResponseEntity.ok(service.getAllOrderByCount());
    }

    @GetMapping("/by-country/{countryId}")
    public ResponseEntity<List<MajorDistribution>> getByCountry(@PathVariable Integer countryId) {
        return ResponseEntity.ok(service.getByCountry(countryId));
    }

    @GetMapping("/by-major/{majorId}")
    public ResponseEntity<List<MajorDistribution>> getByMajor(@PathVariable Integer majorId) {
        return ResponseEntity.ok(service.getByMajor(majorId));
    }

    @GetMapping("/metadata")
    public ResponseEntity<?> getMetadata() {
        return ResponseEntity.ok(service.getMetadata());
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) Integer majorId,
            @RequestParam(required = false) Integer degreeId,
            @RequestParam(required = false) Integer studyTypeId,
            @RequestParam(defaultValue = "ar") String lang) {
        List<MajorDistribution> items = service.search(countryId, countryIds, majorId, degreeId, studyTypeId);
        Map<String, Object> jasperParams = reportParamBuilder.build("MAJOR_DISTRIBUTION", lang);
        return reportExports.render("major-distribution", jasperParams, items, "major-distribution", disposition);
    }

}