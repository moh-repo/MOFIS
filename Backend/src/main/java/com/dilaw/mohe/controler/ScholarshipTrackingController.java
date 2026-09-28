package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.ScholarshipTrackingMetadataDto;
import com.dilaw.mohe.model.ScholarshipTracking;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.ScholarshipTrackingService;
import com.dilaw.mohe.util.SortWhitelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/reports/financial/scholarship-tracking")
@PreAuthorize(MoheSecurityExpressions.REPORT_FINANCE)
public class ScholarshipTrackingController {

    /** Properties of {@link ScholarshipTracking} the list may be ordered by (plan item 2.4). */
    static final java.util.Set<String> SORTABLE = java.util.Set.of(
            "scholarshipId", "studentId", "studentName", "studentPassportNumber", "scholarshipType",
            "scholarshipProvider", "scholarshipAmount", "scholarshipCurrency", "allocatedDate", "startDate",
            "endDate", "amountPaid", "scholarshipStatus", "scholarshipStatusCode",
            "universityName", "majorName");

    @Autowired
    private ScholarshipTrackingService service;

    
    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping("/")
    public ResponseEntity<Page<ScholarshipTracking>> getAll(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        Sort sort = SortWhitelist.sort(sortBy, sortDir, SORTABLE);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ScholarshipTracking> scholarships = service.getByFilters(countryId, countryIds, type, provider, status, search, pageable);
        return ResponseEntity.ok(scholarships);
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ar") String lang) {
        List<ScholarshipTracking> items = service.getByFilters(countryId, countryIds, type, provider, status, search, org.springframework.data.domain.PageRequest.of(0, 500, org.springframework.data.domain.Sort.by("startDate").descending())).getContent();
        Map<String, Object> jasperParams = reportParamBuilder.build("SCHOLARSHIP_TRACKING", lang);
        return reportExports.render("scholarship-tracking", jasperParams, items, "scholarship-tracking", disposition);
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<ScholarshipTracking>> getByType(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String type) {
        return ResponseEntity.ok(service.getByType(countryId, type));
    }

    @GetMapping("/provider/{provider}")
    public ResponseEntity<List<ScholarshipTracking>> getByProvider(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String provider) {
        return ResponseEntity.ok(service.getByProvider(countryId, provider));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<ScholarshipTracking>> getByStatus(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String status) {
        return ResponseEntity.ok(service.getByStatus(countryId, status));
    }
    
    @GetMapping("/metadata")
    public ResponseEntity<ScholarshipTrackingMetadataDto> getMetadata() {
        return ResponseEntity.ok(service.getMetadata());
    }
}

