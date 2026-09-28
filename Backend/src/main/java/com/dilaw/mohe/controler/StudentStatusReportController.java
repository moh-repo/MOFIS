package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.StudentStatusReport;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.audit.ReportAccessLogService;
import com.dilaw.mohe.report.excel.ExcelExportService;
import com.dilaw.mohe.report.excel.ExcelResponses;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.service.StudentStatusReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/reports/student-status")
@PreAuthorize(MoheSecurityExpressions.REPORT_ACADEMIC)
public class StudentStatusReportController {

    @Autowired
    private StudentStatusReportService service;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private ExcelExportService excelExportService;

    @Autowired
    private ReportAccessLogService reportAccessLogService;

    @Autowired(required = false)
    private MessageSource messageSource;

    /**
     * The category codes the report view emits ({@code status_category} in
     * V104: keyed on the status CODE, never on the display name).
     */
    public static final List<String> CATEGORY_CODES =
            List.of("active", "new", "suspended", "withdrawn", "transferred", "other");

    /**
     * {@code active} → "Active" / "نشط", for the PDF's group header and category
     * column. The view groups by a fixed English code so that the grouping
     * survives a renamed status; the code itself was what the PDF printed, beside
     * an Arabic status name (live QA M2, 24 Sep 2026). A code without a message
     * is left out of the map and prints as itself.
     */
    public Map<String, String> categoryLabels(String lang) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (messageSource == null) return labels;
        Locale locale = "en".equalsIgnoreCase(lang) ? Locale.ENGLISH : Locale.forLanguageTag("ar");
        for (String code : CATEGORY_CODES) {
            String label = messageSource.getMessage("report.statusCategory." + code, null, null, locale);
            if (label != null && !label.isBlank()) labels.put(code, label);
        }
        return labels;
    }

    @GetMapping("/search")
    public ResponseEntity<List<StudentStatusReport>> getAll(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) Integer studyStatusId,
            @RequestParam(required = false) Integer statusId,
            @RequestParam(required = false) Integer majorId,
            @RequestParam(required = false) Integer degreeId) {
        Integer effectiveStatusId = studyStatusId != null ? studyStatusId : statusId;
        Integer effectiveMajorId = majorId != null ? majorId : degreeId;
        return ResponseEntity.ok(service.search(effectiveStatusId, countryId, countryIds, effectiveMajorId));
    }

    @GetMapping("/by-category/{category}")
    public ResponseEntity<List<StudentStatusReport>> getByStatusCategory(@PathVariable String category) {
        return ResponseEntity.ok(service.getByStatusCategory(category));
    }

    @GetMapping("/by-country/{countryId}")
    public ResponseEntity<List<StudentStatusReport>> getByCountry(@PathVariable Integer countryId) {
        return ResponseEntity.ok(service.getByCountry(countryId));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getDistinctStatusCategories(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getDistinctStatusCategories(countryId));
    }

    @GetMapping("/metadata")
    public ResponseEntity<?> getMetadata() {
        return ResponseEntity.ok(service.getMetadata());
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) Integer studyStatusId,
            @RequestParam(required = false) Integer statusId,
            @RequestParam(required = false) Integer majorId,
            @RequestParam(required = false) Integer degreeId,
            @RequestParam(defaultValue = "ar") String lang) {

        Integer effectiveStatusId = studyStatusId != null ? studyStatusId : statusId;
        Integer effectiveMajorId = majorId != null ? majorId : degreeId;
        List<StudentStatusReport> items = service.search(effectiveStatusId, countryId, countryIds, effectiveMajorId);

        Map<String, Object> params = reportParamBuilder.build("STUDENT_STATUS", lang);
        params.put("CATEGORY_LABELS", categoryLabels(lang));

        byte[] pdf = jasperReportService.generatePdf("student-status", params, items);
        reportAccessLogService.logExport("STUDENT_STATUS", "PDF",
                "statusId=" + effectiveStatusId + ",countryId=" + countryId + ",majorId=" + effectiveMajorId);
        return PdfResponses.pdf(pdf, "student-status-report.pdf", PdfResponses.isInlineDisposition(disposition));
    }

    @GetMapping("/export-excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) Integer studyStatusId,
            @RequestParam(required = false) Integer statusId,
            @RequestParam(required = false) Integer majorId,
            @RequestParam(required = false) Integer degreeId,
            @RequestParam(defaultValue = "ar") String lang) throws java.io.IOException {

        Integer effectiveStatusId = studyStatusId != null ? studyStatusId : statusId;
        Integer effectiveMajorId = majorId != null ? majorId : degreeId;
        List<StudentStatusReport> items = service.search(effectiveStatusId, countryId, countryIds, effectiveMajorId);

        // Headers and names in the report's language (QA B6 / live QA #12); the
        // view already carries the English names the PDF uses.
        String[] headers = {"report.excel.col.study-status", "report.excel.col.category",
                "report.excel.col.country", "report.excel.col.major", "report.excel.col.student-count"};
        String[] fields  = {"studentStudyStatusName", "statusCategory", "studentCountryName", "studentMajorName", "studentCount"};
        byte[] xlsx = excelExportService.buildWorkbook(lang, "report.excel.student-status.title", null,
                headers, fields, items);
        reportAccessLogService.logExport("STUDENT_STATUS", "EXCEL",
                "statusId=" + effectiveStatusId + ",countryId=" + countryId + ",majorId=" + effectiveMajorId);
        return ExcelResponses.xlsx(xlsx, "student-status-report.xlsx");
    }
}
