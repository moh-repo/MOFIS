package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.PaymentSummaryMetadataDto;
import com.dilaw.mohe.dto.PaymentSummaryTotalsDto;
import com.dilaw.mohe.model.PaymentSummary;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.PaymentSummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.dilaw.mohe.ums.util.UmsPageableUtil;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/reports/financial/payment-summary")
@PreAuthorize(MoheSecurityExpressions.REPORT_FINANCE)
public class PaymentSummaryController {

    /**
     * API sort name → the alias that name is exposed under in the native query's
     * SELECT list.
     *
     * <p>Spring Data appends the sort property to a native query verbatim, so
     * passing the entity property ("paymentDate") produced
     * {@code order by sf.paymentDate}, which is not a column on STUDENT_FINANCE —
     * every one of these sort options failed, and because paymentDate is the
     * default the page could not load at all. The aliases below are what the
     * query actually selects.
     */
    private static final java.util.Map<String, String> PAYMENT_SORT_COLUMNS = java.util.Map.ofEntries(
            java.util.Map.entry("paymentId", "payment_id"),
            java.util.Map.entry("studentId", "student_id"),
            java.util.Map.entry("studentName", "student_name"),
            java.util.Map.entry("paymentDate", "payment_date"),
            java.util.Map.entry("paymentAmount", "payment_amount"),
            java.util.Map.entry("paymentStatus", "payment_status"),
            java.util.Map.entry("paymentType", "payment_type"),
            java.util.Map.entry("paymentMethod", "payment_method"),
            java.util.Map.entry("quarterName", "quarter_name"),
            java.util.Map.entry("yearName", "year_name"));

    private static final String DEFAULT_PAYMENT_SORT = "payment_date";

    /** Accepts either the API name or the alias itself, so existing callers keep working. */
    static String paymentSortColumn(String sortBy) {
        if (sortBy == null) {
            return DEFAULT_PAYMENT_SORT;
        }
        String mapped = PAYMENT_SORT_COLUMNS.get(sortBy);
        if (mapped != null) {
            return mapped;
        }
        return PAYMENT_SORT_COLUMNS.containsValue(sortBy) ? sortBy : DEFAULT_PAYMENT_SORT;
    }
    
    @Autowired
    private PaymentSummaryService service;

    
    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping("/")
    public ResponseEntity<Page<PaymentSummary>> getAll(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String quarter,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "paymentDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        Pageable pageable = UmsPageableUtil.of(page, size, paymentSortColumn(sortBy), sortDir,
                java.util.Set.copyOf(PAYMENT_SORT_COLUMNS.values()), DEFAULT_PAYMENT_SORT);

        Page<PaymentSummary> payments = service.getByFilters(countryId, countryIds, status, type, method, quarter, year, startDate, endDate, search, pageable);
        return ResponseEntity.ok(payments);
    }

    /** The money cards over every row the same filters select, not one page (R6-05). */
    @GetMapping("/totals")
    public ResponseEntity<PaymentSummaryTotalsDto> getTotals(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String quarter,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(service.getTotals(countryId, countryIds, status, type, method, quarter, year, startDate, endDate, search));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String quarter,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ar") String lang) {
        List<PaymentSummary> items = service.getByFilters(countryId, countryIds, status, type, method, quarter, year, startDate, endDate, search, org.springframework.data.domain.PageRequest.of(0, 500)).getContent();
        Map<String, Object> jasperParams = reportParamBuilder.build("PAYMENT_SUMMARY", lang);
        return reportExports.render("payment-summary", jasperParams, items, "payment-summary", disposition);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<PaymentSummary>> getByStatus(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String status) {
        return ResponseEntity.ok(service.getByStatus(countryId, status));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<PaymentSummary>> getByType(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String type) {
        return ResponseEntity.ok(service.getByType(countryId, type));
    }

    @GetMapping("/year/{year}")
    public ResponseEntity<List<PaymentSummary>> getByYear(
            @RequestParam(required = false) Integer countryId,
            @PathVariable Integer year) {
        return ResponseEntity.ok(service.getByYear(countryId, year));
    }
    
    @GetMapping("/metadata")
    public ResponseEntity<PaymentSummaryMetadataDto> getMetadata() {
        return ResponseEntity.ok(service.getMetadata());
    }
}

