package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.AcademicYearReport;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.AcademicYearReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/periodic-reports/academic-year")
@PreAuthorize(MoheSecurityExpressions.REPORT_ACADEMIC)
public class AcademicYearReportController {

    @Autowired
    private AcademicYearReportService service;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<AcademicYearReport>> getAllReports(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getAllReports(countryId, countryIds));
    }

    @GetMapping("/by-year/{year}")
    public ResponseEntity<AcademicYearReport> getReportByAcademicYear(
            @PathVariable String year,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getReportByAcademicYear(year, countryId, countryIds));
    }

    @GetMapping("/by-minimum-graduation-rate")
    public ResponseEntity<List<AcademicYearReport>> getReportsByMinimumGraduationRate(
            @RequestParam BigDecimal rate,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.getReportsByMinimumGraduationRate(rate, countryId, countryIds));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {
        List<AcademicYearReport> items = year != null
                ? java.util.List.of(service.getReportByAcademicYear(year, countryId, countryIds))
                : service.getAllReports(countryId, countryIds);
        Map<String, Object> jasperParams = reportParamBuilder.build("ACADEMIC_YEAR_REPORT", lang);
        return reportExports.render("academic-year-report", jasperParams, items, "academic-year-report", disposition);
    }

}
