package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.UniversityAgreement;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.UniversityAgreementService;
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
@RequestMapping("/api/university-reports/agreements")
@PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
public class UniversityAgreementController {

    @Autowired
    private UniversityAgreementService service;


    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<UniversityAgreement>> getAllAgreements(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAllAgreements(countryId, countryIds));
    }

    @GetMapping("/by-country/{country}")
    public ResponseEntity<List<UniversityAgreement>> getByCountry(
            @PathVariable String country,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getAgreementsByCountry(country, countryId));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {
        List<UniversityAgreement> items = service.getAllAgreements(countryId, countryIds);
        Map<String, Object> jasperParams = reportParamBuilder.build("UNIVERSITY_AGREEMENTS", lang);
        return reportExports.render("university-agreements", jasperParams, items, "university-agreements", disposition);
    }

}
