package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.StudentDistributionUniversity;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.StudentDistributionUniversityService;
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
@RequestMapping("/api/university-reports/student-distribution")
@PreAuthorize(MoheSecurityExpressions.REPORT_ACADEMIC)
public class StudentDistributionUniversityController {

    @Autowired
    private StudentDistributionUniversityService service;


    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<StudentDistributionUniversity>> getAllDistributions(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAllDistributions(countryId, countryIds));
    }

    @GetMapping("/by-country/{country}")
    public ResponseEntity<List<StudentDistributionUniversity>> getByCountry(
            @PathVariable String country,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getDistributionsByCountry(country, countryId));
    }

    @GetMapping("/by-minimum-students")
    public ResponseEntity<List<StudentDistributionUniversity>> getByMinimumStudents(
            @RequestParam Integer students,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getDistributionsByMinimumStudents(students, countryId));
    }

    @GetMapping("/large-universities")
    public ResponseEntity<List<StudentDistributionUniversity>> getLargeUniversities(
            @RequestParam Integer threshold,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getLargeUniversities(threshold, countryId));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {
        List<StudentDistributionUniversity> items = service.getAllDistributions(countryId, countryIds);
        Map<String, Object> jasperParams = reportParamBuilder.build("STUDENT_DIST_UNIVERSITY", lang);
        return reportExports.render("student-distribution-university", jasperParams, items, "student-distribution-university", disposition);
    }

}
