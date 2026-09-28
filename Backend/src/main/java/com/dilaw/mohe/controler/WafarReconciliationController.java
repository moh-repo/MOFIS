package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.WafarReconciliation;
import com.dilaw.mohe.service.WafarReconciliationService;
import com.dilaw.mohe.util.SortWhitelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wafar reconciliation: reads under {@link MoheSecurityExpressions#WAFAR} or
 * {@link MoheSecurityExpressions#FINANCE}, writes under
 * {@link MoheSecurityExpressions#WAFAR_WRITE}. Running a pass and resolving a
 * discrepancy both change ledger state, and until now the class-level "VIEW or EDIT"
 * gate was the only one on them — the same gap the finance controller had (A-phase
 * write authorization).
 *
 * <p>The page sits in the Wafar menu and opens with WAFAR; its reads needed FINANCE, so
 * a Wafar viewer opened it to "Access denied" (live QA M6).
 */
@RestController
@RequestMapping("api/v1/wafar-reconciliation")
@PreAuthorize(MoheSecurityExpressions.WAFAR + " or " + MoheSecurityExpressions.FINANCE)
public class WafarReconciliationController {

    @Autowired
    private WafarReconciliationService service;

    /** The columns the list may be ordered by; the first is the default. */
    private static final Set<String> SORTABLE = new LinkedHashSet<>(List.of(
            "studentId", "reconDate", "moheAmount", "wafarAmount", "delta", "reconStatus"));

    /**
     * Server-side order: the headers were marked sortable but nothing was sent, so a
     * click flipped the arrow and left the rows as they were (QA FIN-002).
     */
    @GetMapping
    public Page<WafarReconciliation> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        PageRequest pageable = PageRequest.of(page, size, SortWhitelist.sort(sortBy, sortDir, SORTABLE));
        if (status != null && !status.isBlank()) {
            return service.getByStatus(status, pageable);
        }
        return service.getAll(pageable);
    }

    /** Paged like {@link #getAll}: the unpaged list returned every open discrepancy in one body. */
    @GetMapping("/discrepancies")
    public Page<WafarReconciliation> getDiscrepancies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.getDiscrepancies(PageRequest.of(page, size));
    }

    @GetMapping("/summary")
    public Map<String, Long> getSummary() {
        return service.getSummary();
    }

    @PostMapping("/run")
    @PreAuthorize(MoheSecurityExpressions.WAFAR_WRITE) // N-04.b: reconciliation follows Wafar ownership
    public ResponseEntity<Map<String, Object>> runReconciliation() {
        int discrepancies = service.runReconciliation();
        return ResponseEntity.ok(Map.of(
            "message", "Reconciliation completed",
            "discrepanciesFound", discrepancies
        ));
    }

    @PutMapping("/{id}/resolve")
    @PreAuthorize(MoheSecurityExpressions.WAFAR_WRITE) // N-04.b: reconciliation follows Wafar ownership
    public WafarReconciliation resolve(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "") String notes) {
        return service.resolve(id, notes);
    }
}
