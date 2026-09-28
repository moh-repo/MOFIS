package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.StudentDemographics;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.service.StudentDemographicsMetadataService;
import com.dilaw.mohe.service.StudentDemographicsService;
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
@RequestMapping("/api/reports/student-demographics")
@PreAuthorize(MoheSecurityExpressions.REPORT_ACADEMIC)
public class StudentDemographicsController {

    @Autowired
    private StudentDemographicsService service;

    @Autowired
    private StudentDemographicsMetadataService metadataService;


    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping("/metadata")
    public ResponseEntity<?> getMetadata() {
        return ResponseEntity.ok(metadataService.getMetadata());
    }

    @GetMapping("/search")
    public ResponseEntity<List<StudentDemographics>> search(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) Integer genderId,
            @RequestParam(required = false) String ageRange,
            @RequestParam(required = false) Integer majorId,
            @RequestParam(required = false) Integer degreeId,
            @RequestParam(required = false) Integer studyTypeId,
            @RequestParam(required = false) Integer agencyTypeId,
            @RequestParam(required = false) Integer studyStatusId) {
        return ResponseEntity.ok(service.search(countryId, countryIds, genderId, ageRange, majorId, degreeId, studyTypeId, agencyTypeId, studyStatusId));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) Integer genderId,
            @RequestParam(required = false) String ageRange,
            @RequestParam(required = false) Integer majorId,
            @RequestParam(required = false) Integer degreeId,
            @RequestParam(required = false) Integer studyTypeId,
            @RequestParam(required = false) Integer agencyTypeId,
            @RequestParam(required = false) Integer studyStatusId,
            @RequestParam(defaultValue = "ar") String lang) {
        List<StudentDemographics> items = service.search(countryId, countryIds, genderId, ageRange, majorId, degreeId, studyTypeId, agencyTypeId, studyStatusId);
        Map<String, Object> jasperParams = reportParamBuilder.build("STUDENT_DEMOGRAPHICS", lang);
        return reportExports.render("student-demographics", jasperParams, items, "student-demographics", disposition);
    }

}