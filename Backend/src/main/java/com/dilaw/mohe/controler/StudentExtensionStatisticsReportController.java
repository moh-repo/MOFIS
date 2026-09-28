package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.StudentExtensionReportMetadataDto;
import com.dilaw.mohe.dto.StudentExtensionStatisticsDto;
import com.dilaw.mohe.dto.StudentExtensionTrendDto;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.StudentExtensionStatisticsReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@PreAuthorize(MoheSecurityExpressions.REPORT_OTHER)
@RestController
@RequestMapping("/api/reports/statistical/student-extension")
@RequiredArgsConstructor
public class StudentExtensionStatisticsReportController {

    private final StudentExtensionStatisticsReportService reportService;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping("/metadata")
    public ResponseEntity<StudentExtensionReportMetadataDto> metadata() {
        return ResponseEntity.ok(reportService.metadata());
    }

    @GetMapping("/statistics")
    public ResponseEntity<List<StudentExtensionStatisticsDto>> statistics(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(value = "year", required = false) Integer year) {
        return ResponseEntity.ok(reportService.statistics(countryId, countryIds, year));
    }

    @GetMapping("/trends")
    public ResponseEntity<List<StudentExtensionTrendDto>> trends(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(reportService.trends(countryId, countryIds));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(value = "extensionType", required = false) String extensionType,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(defaultValue = "ar") String lang) {
        // RPT-06: the year the page filters by reaches the export; it used to be accepted and dropped.
        List<StudentExtensionStatisticsDto> items =
                reportService.statisticsFiltered(countryId, countryIds, extensionType, year);
        Map<String, Object> jasperParams = reportParamBuilder.build("STUDENT_EXTENSION", lang);
        return reportExports.render("student-extension-statistics", jasperParams, items, "student-extension-statistics", disposition);
    }

}
