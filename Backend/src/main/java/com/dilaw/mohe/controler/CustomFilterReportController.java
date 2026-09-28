package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.CustomFilterReport;
import com.dilaw.mohe.audit.ReportAccessLogService;
import com.dilaw.mohe.report.excel.ExcelExportService;
import com.dilaw.mohe.report.excel.ExcelResponses;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.CustomFilterReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;

import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/custom-reports/custom-filter")
@PreAuthorize(MoheSecurityExpressions.REPORT_OTHER)
public class CustomFilterReportController {

    @Autowired
    private CustomFilterReportService service;


    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private ExcelExportService excelExportService;

    @Autowired
    private ReportAccessLogService reportAccessLogService;

    @GetMapping
    public ResponseEntity<List<CustomFilterReport>> getAllReports(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getAllReports(countryId));
    }

    @GetMapping("/by-study-status/{statusId}")
    public ResponseEntity<List<CustomFilterReport>> getByStudyStatus(
            @PathVariable Integer statusId,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getReportsByStudyStatus(statusId, countryId));
    }

    /** Alias used by the UI filter route. */
    @GetMapping("/by-status/{statusId}")
    public ResponseEntity<List<CustomFilterReport>> getByStatus(
            @PathVariable Integer statusId,
            @RequestParam(required = false) Integer countryId) {
        return getByStudyStatus(statusId, countryId);
    }

    @GetMapping("/by-degree-level/{degreeId}")
    public ResponseEntity<List<CustomFilterReport>> getByDegreeLevel(
            @PathVariable Integer degreeId,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getReportsByDegreeLevel(degreeId, countryId));
    }

    @GetMapping("/by-study-type/{typeId}")
    public ResponseEntity<List<CustomFilterReport>> getByStudyType(
            @PathVariable Integer typeId,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getReportsByStudyType(typeId, countryId));
    }

    @GetMapping("/by-university/{name}")
    public ResponseEntity<List<CustomFilterReport>> getByUniversityName(
            @PathVariable String name,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getReportsByUniversityName(name, countryId));
    }

    @GetMapping("/by-enrollment-year/{year}")
    public ResponseEntity<List<CustomFilterReport>> getByEnrollmentYear(
            @PathVariable Integer year,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getReportsByEnrollmentYear(year, countryId));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer studyStatus,
            @RequestParam(required = false) Integer statusId,
            @RequestParam(required = false) Integer degreeLevel,
            @RequestParam(required = false) Integer degreeLevelId,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(defaultValue = "ar") String lang) {
        Integer effectiveStatus = studyStatus != null ? studyStatus : statusId;
        Integer effectiveDegree = degreeLevel != null ? degreeLevel : degreeLevelId;
        List<CustomFilterReport> items;
        if (effectiveStatus != null) {
            items = service.getReportsByStudyStatus(effectiveStatus, countryId);
        } else if (effectiveDegree != null) {
            items = service.getReportsByDegreeLevel(effectiveDegree, countryId);
        } else {
            items = service.getAllReports(countryId);
        }
        Map<String, Object> jasperParams = reportParamBuilder.build("CUSTOM_FILTER_REPORT", lang);
        byte[] pdf = jasperReportService.generatePdf("custom-filter-report", jasperParams, items);
        reportAccessLogService.logExport("CUSTOM_FILTER", "PDF",
                "status=" + effectiveStatus + ",degree=" + effectiveDegree + ",country=" + countryId);
        return PdfResponses.pdf(pdf, "custom-filter-report.pdf", PdfResponses.isInlineDisposition(disposition));
    }

    @GetMapping("/export-excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) Integer studyStatus,
            @RequestParam(required = false) Integer statusId,
            @RequestParam(required = false) Integer degreeLevel,
            @RequestParam(required = false) Integer degreeLevelId,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(defaultValue = "ar") String lang) throws java.io.IOException {

        Integer effectiveStatus = studyStatus != null ? studyStatus : statusId;
        Integer effectiveDegree = degreeLevel != null ? degreeLevel : degreeLevelId;
        List<CustomFilterReport> items;
        if (effectiveStatus != null) {
            items = service.getReportsByStudyStatus(effectiveStatus, countryId);
        } else if (effectiveDegree != null) {
            items = service.getReportsByDegreeLevel(effectiveDegree, countryId);
        } else {
            items = service.getAllReports(countryId);
        }
        // Headers and names in the report's language (QA B6 / live QA #12).
        String[] headers = {"report.excel.col.student-name", "report.excel.col.passport-number",
                "report.excel.col.university", "report.excel.col.major", "report.excel.col.country",
                "report.excel.col.degree", "report.excel.col.study-status", "report.excel.col.study-type",
                "report.excel.col.financial-amount", "report.excel.col.enrollment-year"};
        String[] fields  = {"studentName", "nationalNumber", "universityName", "majorName", "countryName", "degreeLevelName", "studyStatusName", "studyTypeName", "financialAmount", "enrollmentYear"};
        byte[] xlsx = excelExportService.buildWorkbook(lang, "report.excel.custom-filter.title", null,
                headers, fields, items);
        reportAccessLogService.logExport("CUSTOM_FILTER", "EXCEL",
                "status=" + effectiveStatus + ",degree=" + effectiveDegree + ",country=" + countryId);
        return ExcelResponses.xlsx(xlsx, "custom-filter-report.xlsx");
    }

}
