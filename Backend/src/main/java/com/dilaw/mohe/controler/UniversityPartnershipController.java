package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.UniversityPartnership;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.UniversityPartnershipService;
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
@RequestMapping("/api/university-reports/partnerships")
@PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
public class UniversityPartnershipController {

    @Autowired
    private UniversityPartnershipService service;


    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<UniversityPartnership>> getAllPartnerships(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAllPartnerships(countryId, countryIds));
    }

    @GetMapping("/by-country/{country}")
    public ResponseEntity<List<UniversityPartnership>> getByCountry(
            @PathVariable String country,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getPartnershipsByCountry(country, countryId));
    }

    @GetMapping("/by-minimum-students")
    public ResponseEntity<List<UniversityPartnership>> getByMinimumStudents(
            @RequestParam Integer students,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getPartnershipsByMinimumStudents(students, countryId));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {
        List<UniversityPartnership> items = service.getAllPartnerships(countryId, countryIds);
        Map<String, Object> jasperParams = reportParamBuilder.build("UNIVERSITY_PARTNERSHIPS", lang);
        return reportExports.render("university-partnerships", jasperParams, items, "university-partnerships", disposition);
    }

}
