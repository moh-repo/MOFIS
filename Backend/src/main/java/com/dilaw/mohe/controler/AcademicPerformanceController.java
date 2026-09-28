package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.AcademicPerformance;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.AcademicPerformanceMetadataService;
import com.dilaw.mohe.service.AcademicPerformanceService;
import com.dilaw.mohe.ums.service.CountryScopeService;
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
@RequestMapping("/api/reports/academic-performance")
@PreAuthorize(MoheSecurityExpressions.REPORT_ACADEMIC)
public class AcademicPerformanceController {

    @Autowired
    private AcademicPerformanceService service;

    @Autowired
    private AcademicPerformanceMetadataService metadataService;


    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<AcademicPerformance>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/by-country/{countryId}")
    public ResponseEntity<List<AcademicPerformance>> getByCountry(@PathVariable Integer countryId) {
        return ResponseEntity.ok(service.getByCountry(countryId));
    }

    @GetMapping("/by-major/{majorId}")
    public ResponseEntity<List<AcademicPerformance>> getByMajor(@PathVariable Integer majorId) {
        return ResponseEntity.ok(service.getByMajor(majorId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<AcademicPerformance>> getByFilters(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) Integer majorId,
            @RequestParam(required = false) Integer degreeId,
            @RequestParam(required = false) Integer studyTypeId,
            @RequestParam(required = false) Integer studyStatusId) {
        return ResponseEntity.ok(service.getByFilters(countryId, countryIds, majorId, degreeId, studyTypeId, studyStatusId));
    }

    @GetMapping("/metadata")
    public ResponseEntity<?> getMetadata() {
        return ResponseEntity.ok(metadataService.getMetadata());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AcademicPerformance> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) Integer majorId,
            @RequestParam(required = false) Integer degreeId,
            @RequestParam(required = false) Integer studyTypeId,
            @RequestParam(required = false) Integer studyStatusId,
            @RequestParam(defaultValue = "ar") String lang) {
        List<AcademicPerformance> items = service.getByFilters(countryId, countryIds, majorId, degreeId, studyTypeId, studyStatusId);
        Map<String, Object> jasperParams = reportParamBuilder.build("ACADEMIC_PERFORMANCE", lang);
        return reportExports.render("academic-performance", jasperParams, items, "academic-performance", disposition);
    }

}