package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.FinancialAuditMetadataDto;
import com.dilaw.mohe.model.FinancialAudit;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.FinancialAuditMetadataService;
import com.dilaw.mohe.service.FinancialAuditService;
import com.dilaw.mohe.util.SortWhitelist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/reports/financial/financial-audit")
@PreAuthorize(MoheSecurityExpressions.REPORT_FINANCE)
public class FinancialAuditController {

    /** Properties of {@link FinancialAudit} the list may be ordered by (plan item 2.4). */
    static final Set<String> SORTABLE = Set.of(
            "auditId", "auditDate", "auditCategory", "auditAmount", "auditCurrency", "auditStatus",
            "auditorName", "studentId", "studentName", "studentPassport", "bankName", "fiscalYear", "fiscalQuarter");

    private final FinancialAuditService service;
    private final FinancialAuditMetadataService metadataService;

    public FinancialAuditController(FinancialAuditService service,
                                    FinancialAuditMetadataService metadataService) {
        this.service = service;
        this.metadataService = metadataService;
    }

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping("/")
    public ResponseEntity<Page<FinancialAudit>> getAll(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) String bank,
            @RequestParam(required = false) String auditor,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "auditDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        Sort sort = SortWhitelist.sort(sortBy, sortDir, SORTABLE);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<FinancialAudit> audits = service.getByFilters(countryId, countryIds, status, category, year, quarter, bank, auditor, currency, startDate, endDate, search, pageable);
        return ResponseEntity.ok(audits);
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) String bank,
            @RequestParam(required = false) String auditor,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ar") String lang) {
        List<FinancialAudit> items = service.getByFilters(countryId, countryIds, status, category, year, quarter, bank, auditor, currency, startDate, endDate, search, org.springframework.data.domain.PageRequest.of(0, 500, org.springframework.data.domain.Sort.by("auditDate").descending())).getContent();
        Map<String, Object> jasperParams = reportParamBuilder.build("FINANCIAL_AUDIT", lang);
        return reportExports.render("financial-audit", jasperParams, items, "financial-audit", disposition);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<FinancialAudit>> getByStatus(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String status) {
        return ResponseEntity.ok(service.getByStatus(countryId, status));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<FinancialAudit>> getByCategory(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String category) {
        return ResponseEntity.ok(service.getByCategory(countryId, category));
    }

    @GetMapping("/year/{year}")
    public ResponseEntity<List<FinancialAudit>> getByYear(
            @RequestParam(required = false) Integer countryId,
            @PathVariable Integer year) {
        return ResponseEntity.ok(service.getByYear(countryId, year));
    }
    
    @GetMapping("/metadata")
    public ResponseEntity<FinancialAuditMetadataDto> getMetadata() {
        return ResponseEntity.ok(metadataService.getMetadata());
    }
}

