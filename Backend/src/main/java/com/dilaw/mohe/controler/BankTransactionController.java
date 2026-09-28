package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.BankTransactionMetadataDto;
import com.dilaw.mohe.model.BankTransaction;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.BankTransactionService;
import com.dilaw.mohe.service.BankTransactionMetadataService;
import com.dilaw.mohe.util.SortWhitelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/reports/financial/bank-transaction")
@PreAuthorize(MoheSecurityExpressions.REPORT_FINANCE)
public class BankTransactionController {

    /** Properties of {@link BankTransaction} the list may be ordered by (plan item 2.4). */
    static final java.util.Set<String> SORTABLE = java.util.Set.of(
            "transactionId", "transactionDate", "transactionType", "transactionDirection", "transactionDescription",
            "transactionAmount", "transactionCurrency", "transactionCurrencyEn", "referenceNumber", "bankName",
            "bankNameEn", "bankBranch", "bankBranchEn", "studentId", "beneficiaryName", "beneficiaryAccount");

    @Autowired
    private BankTransactionService service;

    @Autowired
    private BankTransactionMetadataService metadataService;

    
    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping("/")
    public ResponseEntity<Page<BankTransaction>> getAll(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String bankName,
            @RequestParam(required = false) String branchName,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "transactionDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        Sort sort = SortWhitelist.sort(sortBy, sortDir, SORTABLE);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<BankTransaction> transactions = service.getByFilters(countryId, countryIds, type, bankName, branchName, currency, startDate, endDate, search, pageable);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String bankName,
            @RequestParam(required = false) String branchName,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ar") String lang) {
        List<BankTransaction> items = service.getByFilters(countryId, countryIds, type, bankName, branchName, currency, startDate, endDate, search, org.springframework.data.domain.PageRequest.of(0, 500, org.springframework.data.domain.Sort.by("transactionDate").descending())).getContent();
        Map<String, Object> jasperParams = reportParamBuilder.build("BANK_TRANSACTION", lang);
        return reportExports.render("bank-transaction", jasperParams, items, "bank-transactions", disposition);
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<BankTransaction>> getByType(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String type) {
        return ResponseEntity.ok(service.getByType(countryId, type));
    }

    @GetMapping("/bank/{bankName}")
    public ResponseEntity<List<BankTransaction>> getByBankName(
            @RequestParam(required = false) Integer countryId,
            @PathVariable String bankName) {
        return ResponseEntity.ok(service.getByBankName(countryId, bankName));
    }
    
    @GetMapping("/metadata")
    public ResponseEntity<BankTransactionMetadataDto> getMetadata() {
        return ResponseEntity.ok(metadataService.getMetadata());
    }
}

