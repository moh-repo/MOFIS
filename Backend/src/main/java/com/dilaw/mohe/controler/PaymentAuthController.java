package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.PaymentAuthBulkAuthorizeRequest;
import com.dilaw.mohe.model.PaymentAuth;
import com.dilaw.mohe.report.AmountInWords;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.service.LuPaymentAuthStatusService;
import com.dilaw.mohe.service.PaymentAuthService;
import com.dilaw.mohe.util.SortWhitelist;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Payment authorisations (استمارة اعتماد الصرف).
 *
 * <p>The class-level {@code PAYMENT_AUTH} gate is for reads: it admits
 * {@code FINANCE.VIEW} and {@code PAYMENT_AUTH.VIEW}, so until the 27 Aug 2026
 * pass (plan item 1.2) a read-only finance role could create, edit, delete and
 * — through {@code /bulk-authorize} — release payments. Writes now carry
 * {@code PAYMENT_AUTH_WRITE}; release carries {@code PAYMENT_AUTH_APPROVE}, the
 * APPROVE code alone. A method-level annotation replaces the class-level one.
 */
@PreAuthorize(MoheSecurityExpressions.PAYMENT_AUTH)
@RestController
@RequestMapping("api/v1/paymentAuth")
public class PaymentAuthController {

    /** Properties of {@link PaymentAuth} the list may be ordered by (plan item 2.4). */
    static final Set<String> SORTABLE = Set.of(
            "id", "authFormNumber", "authDate", "studentId", "studentName", "ministryName", "deptName",
            "provinceName", "bankName", "bankBranch", "sentTo", "amountSource", "originalAmount",
            "deductions", "netAmount", "paymentPurpose", "checkNumber", "checkDate", "budgetBookNumber",
            "recipientName", "authStatus", "createdBy", "authorizedBy", "authMidate");

    @Autowired
    private PaymentAuthService service;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.repository.StudyCurrencyInRepository currencyRepository;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @Autowired
    private LuPaymentAuthStatusService luPaymentAuthStatusService;

    @GetMapping("/")
    public Page<PaymentAuth> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) String status) {
        Sort sort = SortWhitelist.sort(sortBy, sortDir, SORTABLE);
        Pageable pageable = PageRequest.of(page, size, sort);
        return (searchTerm != null && !searchTerm.isBlank()) || status != null
                ? service.search(searchTerm, status, pageable)
                : service.getAll(pageable);
    }

    @GetMapping("/all")
    public List<PaymentAuth> getAllList() {
        return service.getAllList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentAuth> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/by-student/{studentId}")
    public List<PaymentAuth> getByStudent(@PathVariable Integer studentId) {
        return service.getByStudentId(studentId);
    }

    /**
     * Calculate the total payable amount for a student from the requested sources.
     * @param studentId  the student
     * @param sources    comma-separated: FINANCE, WAFAR, UNIVERSITY_FEE, COMBINATION, ALL
     */
    @GetMapping("/calculate-amount")
    public ResponseEntity<Map<String, Object>> calculateAmount(
            @RequestParam Integer studentId,
            @RequestParam(defaultValue = "ALL") String sources) {
        return ResponseEntity.ok(service.calculateAmount(studentId, sources));
    }

    @PostMapping("/")
    @PreAuthorize(MoheSecurityExpressions.PAYMENT_AUTH_WRITE)
    public ResponseEntity<PaymentAuth> create(@Valid @RequestBody PaymentAuth entity) {
        return ResponseEntity.ok(service.create(entity));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.PAYMENT_AUTH_WRITE)
    public ResponseEntity<PaymentAuth> update(@PathVariable Integer id, @Valid @RequestBody PaymentAuth entity) {
        return ResponseEntity.ok(service.update(id, entity));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.PAYMENT_AUTH_WRITE)
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Release a list of DRAFT / PENDING payment records, best effort per row.
     * Body: { "paymentIds": [1, 2, 3] }
     * Returns: { authorized: N, failed: [{id, reason}...], authorizedBy, authorizedAt }
     * — see {@link PaymentAuthService#bulkAuthorize} for what "failed" does and
     * does not cover.
     */
    @PostMapping("/bulk-authorize")
    @PreAuthorize(MoheSecurityExpressions.PAYMENT_AUTH_APPROVE)
    public ResponseEntity<Map<String, Object>> bulkAuthorize(@Valid @RequestBody PaymentAuthBulkAuthorizeRequest body) {
        return ResponseEntity.ok(service.bulkAuthorize(body.paymentIds()));
    }

    /** Released payment paid out: APPROVED to PAID (review F6). Same permission as releasing it. */
    @PostMapping("/{id}/mark-paid")
    @PreAuthorize(MoheSecurityExpressions.PAYMENT_AUTH_APPROVE)
    public ResponseEntity<PaymentAuth> markPaid(@PathVariable Integer id) {
        return ResponseEntity.ok(service.markPaid(id));
    }

    /**
     * Status list for the filter dropdowns. Read from LU_PAYMENT_AUTH_STATUS so
     * adding a status is a data change; the codes were previously duplicated
     * here as literals and could drift from the lookup table.
     */
    @GetMapping("/metadata")
    public ResponseEntity<Map<String, Object>> getMetadata() {
        List<Map<String, String>> statuses = luPaymentAuthStatusService.getAll().stream()
                .filter(s -> s.getCode() != null && !s.getCode().isBlank())
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .sorted(java.util.Comparator.comparing(
                        s -> s.getSortOrder() != null ? s.getSortOrder() : Integer.MAX_VALUE))
                .map(s -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("name", s.getCode());
                    m.put("nameAr", s.getNameAr() != null ? s.getNameAr() : s.getCode());
                    m.put("nameEn", s.getNameEn() != null ? s.getNameEn() : s.getCode());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(Map.of("statuses", statuses));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "ar") String lang) {

        List<PaymentAuth> items = status != null && !status.isBlank()
                ? service.getAllList().stream()
                    .filter(p -> status.equalsIgnoreCase(p.getAuthStatus()))
                    .toList()
                : service.getAllList();

        Map<String, Object> params = reportParamBuilder.build("PAYMENT_AUTH", lang);
        params.put("STATUS_NAMES", statusNames(lang));

        return reportExports.render("payment-auth", params, items, "payment-auth-report", disposition);
    }

    /**
     * Status code → localized name, so the export prints "معتمد" rather than the
     * stored code "APPROVED". Codes with no lookup row are left to fall back to
     * the raw value in the template rather than printing blank.
     */
    private Map<String, String> statusNames(String lang) {
        boolean rtl = !"en".equalsIgnoreCase(lang);
        Map<String, String> names = new LinkedHashMap<>();
        for (var s : luPaymentAuthStatusService.getAll()) {
            if (s.getCode() == null || s.getCode().isBlank() || Boolean.TRUE.equals(s.getIsDeleted())) {
                continue;
            }
            String name = rtl ? s.getNameAr() : s.getNameEn();
            names.put(s.getCode(), name != null && !name.isBlank() ? name : s.getCode());
        }
        return names;
    }

    /**
     * The authorisation voucher itself — "استمارة اعتماد الصرف" (نموذج رقم ١١
     * حسابات) — for a single record, as opposed to {@link #exportReport} which
     * lists many. Rendered from the prescribed payment-auth-form template so the
     * printed document is the statutory form rather than a screen replica.
     *
     * <p>Carries the spreadsheet aliases for the same reason every other report
     * does: the shared action bar derives the Excel path by rewriting
     * {@code /export-report} to {@code /export-excel}, so without them the
     * Export button on the voucher would ask for a path that is not mapped. Going
     * through {@link com.dilaw.mohe.report.ReportExports} rather than calling
     * Jasper directly is what keeps the spreadsheet and the PDF the same report —
     * a form-layout template makes a plain data dump of itself, not a facsimile.
     */
    @GetMapping(value = {"/{id}/export-report", "/{id}/report-pdf", "/{id}/doc",
            "/{id}/export-excel", "/{id}/report-xlsx"})
    public ResponseEntity<byte[]> exportForm(
            @PathVariable Integer id,
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(defaultValue = "ar") String lang) {

        PaymentAuth record = service.getById(id);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment authorization not found");
        }

        Map<String, Object> params = reportParamBuilder.build("PAYMENT_AUTH_FORM", lang);
        addCurrencyAndWords(params, record, !"en".equalsIgnoreCase(lang));

        String baseName = "payment-auth-" + (record.getAuthFormNumber() != null
                ? record.getAuthFormNumber() : String.valueOf(id));
        return reportExports.render("payment-auth-form", params, List.of(record), baseName, disposition);
    }

    /**
     * The currency the voucher is denominated in, and its total written out.
     *
     * <p>The currency is the ministry's own — {@code STUDY_CURRENCY_IN.IS_FUNCTIONAL},
     * which a filtered unique index keeps to exactly one row. A disbursement on نموذج
     * رقم ١١ is paid in the national currency, so it is a property of the ministry
     * rather than of the record; {@code PAYMENT_AUTH} has no currency column and this
     * pass did not invent one.
     *
     * <p>The words are التفقيط, and they are of the <em>net</em> amount: the figure
     * beside them on the total row is what is actually to be paid. If no currency has
     * been flagged, both come out blank and the template omits them rather than
     * printing a total in an unnamed currency.
     */
    // Package-private so the mapping can be tested on its own: it is the link between
    // a database row and two template parameters, and a rendered PDF is an awkward
    // place to look for a missing one.
    void addCurrencyAndWords(Map<String, Object> params, PaymentAuth record, boolean arabic) {
        AmountInWords.Currency currency = currencyRepository.findFirstByIsFunctionalTrue()
                .map(c -> new AmountInWords.Currency(
                        c.getIsoCode(),
                        c.getStudyCurrencyInName(),
                        c.getStudyCurrencyInNameEn(),
                        c.getMinorUnits() != null ? c.getMinorUnits() : 2))
                .orElse(null);

        params.put("CURRENCY_NAME", currency == null ? ""
                : nvl(arabic ? currency.nameAr() : currency.nameEn()));
        params.put("AMOUNT_IN_WORDS", currency == null ? ""
                : AmountInWords.of(record.getNetAmount(), currency, arabic));
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
