package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.EnrollmentStatisticsRowDto;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.service.EnrollmentStatisticsMetadataService;
import com.dilaw.mohe.service.EnrollmentStatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;

@RestController
@RequestMapping("/api/reports/enrollment-statistics")
@PreAuthorize(MoheSecurityExpressions.REPORT_ACADEMIC)
public class EnrollmentStatisticsController {

    @Autowired
    private EnrollmentStatisticsService service;

    @Autowired
    private EnrollmentStatisticsMetadataService metadataService;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping("/metadata")
    public ResponseEntity<?> getMetadata(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "ar") String lang) {
        // Options are scoped the same way the data is, so the dropdowns can never
        // offer a value the user is not allowed to see.
        return ResponseEntity.ok(metadataService.getMetadata(lang, countryId, countryIds));
    }

    @GetMapping("/search")
    public ResponseEntity<List<EnrollmentStatisticsRowDto>> search(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) Integer majorId,
            @RequestParam(required = false) Integer degreeId,
            @RequestParam(required = false) Integer studyTypeId,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(defaultValue = "ar") String lang) {
        return ResponseEntity.ok(service.search(quarter, countryId, countryIds, majorId, degreeId, studyTypeId, lang));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) Integer majorId,
            @RequestParam(required = false) Integer degreeId,
            @RequestParam(required = false) Integer studyTypeId,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(defaultValue = "ar") String lang) {
        List<EnrollmentStatisticsRowDto> items =
                service.search(quarter, countryId, countryIds, majorId, degreeId, studyTypeId, lang);
        Map<String, Object> jasperParams = reportParamBuilder.build("ENROLLMENT_STATISTICS", lang);

        // Totals the template prints, computed from the same rows it renders.
        long totalStudents = items.stream()
                .map(EnrollmentStatisticsRowDto::getStudentCount)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
        jasperParams.put("TOTAL_STUDENTS", totalStudents);
        jasperParams.put("TOTAL_ROWS", items.size());
        jasperParams.put("TOTAL_COUNTRIES", distinctCount(items, EnrollmentStatisticsRowDto::getStudentCountryId));
        jasperParams.put("TOTAL_MAJORS", distinctCount(items, EnrollmentStatisticsRowDto::getStudentMajorId));

        return reportExports.render("enrollment-statistics", jasperParams, items, "enrollment-statistics", disposition);
    }

    private static int distinctCount(List<EnrollmentStatisticsRowDto> items,
                                     java.util.function.Function<EnrollmentStatisticsRowDto, Integer> key) {
        return (int) items.stream().map(key).filter(java.util.Objects::nonNull).distinct().count();
    }

}