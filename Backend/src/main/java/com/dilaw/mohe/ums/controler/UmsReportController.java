package com.dilaw.mohe.ums.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.ums.model.UmsReport;
import com.dilaw.mohe.ums.service.UmsReportService;
import com.dilaw.mohe.ums.util.UmsPageableUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("api/v1/ums/reports")
@PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_VIEW)
public class UmsReportController {

    private static final Set<String> REPORT_SORT_FIELDS = Set.of(
            "id", "reportCode", "reportName", "category", "route", "isActive", "createdAt");

    @Autowired
    private UmsReportService reportService;

    @GetMapping("/")
    public ResponseEntity<Page<UmsReport>> getAllReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "reportCode") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        Pageable pageable = UmsPageableUtil.of(page, size, sortBy, sortDir, REPORT_SORT_FIELDS, "reportCode");
        return ResponseEntity.ok(reportService.listReports(blankToNull(keyword), blankToNull(category),
                pageable));
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @GetMapping("/all")
    public ResponseEntity<List<UmsReport>> getAllActiveReports() {
        return ResponseEntity.ok(reportService.getAllActiveReports());
    }

    /**
     * The report catalogue (V073): every active hub and card with both names,
     * icon, colour, route, query string, parent and guard codes. Any signed-in
     * user may read it; the frontend hides what the caller's permissions do
     * not open, as the hub pages always did.
     */
    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UmsReport>> catalogue() {
        return ResponseEntity.ok(reportService.catalogue());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UmsReport> getReportById(@PathVariable Long id) {
        return reportService.getReportById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_ADD)
    public ResponseEntity<UmsReport> createReport(@Valid @RequestBody UmsReport report) {
        return ResponseEntity.ok(reportService.createReport(report));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_EDIT)
    public ResponseEntity<UmsReport> updateReport(@PathVariable Long id, @Valid @RequestBody UmsReport report) {
        return ResponseEntity.ok(reportService.updateReport(id, report));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.ADMIN_TOOLS_DELETE)
    public ResponseEntity<Void> deleteReport(@PathVariable Long id) {
        reportService.deleteReport(id);
        return ResponseEntity.noContent().build();
    }
}
