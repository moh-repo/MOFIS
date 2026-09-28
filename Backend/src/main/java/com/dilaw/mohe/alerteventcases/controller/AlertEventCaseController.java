package com.dilaw.mohe.alerteventcases.controller;

import com.dilaw.mohe.alerteventcases.dto.AlertEventCaseDto;
import com.dilaw.mohe.alerteventcases.dto.AlertEventCaseSummaryDto;
import com.dilaw.mohe.alerteventcases.service.AlertEventCaseService;
import com.dilaw.mohe.alerteventcases.service.AlertEventDetectionService;
import com.dilaw.mohe.config.MoheSecurityExpressions;
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

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/alert-events")
public class AlertEventCaseController {

    /**
     * Properties of {@code AlertEventCase} the list may be ordered by (plan item
     * 2.4). The dotted entries are the two associations the grid shows columns for.
     */
    static final Set<String> SORTABLE = Set.of(
            "caseId", "status", "referenceDate", "createdAt", "updatedAt", "acknowledgedAt", "resolvedAt",
            "acknowledgedBy", "resolvedBy", "studyCountryInId",
            "scenario.scenarioCode", "scenario.severity", "student.studentName");

    @Autowired
    private AlertEventCaseService caseService;

    @Autowired
    private AlertEventDetectionService detectionService;

    @GetMapping
    @PreAuthorize(MoheSecurityExpressions.ALERT_EVENTS_VIEW)
    public Page<AlertEventCaseDto> getAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scenarioCode,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Integer studentId,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = SortWhitelist.sort(sortBy, sortDir, SORTABLE);
        Pageable pageable = PageRequest.of(page, size, sort);
        return caseService.getAll(status, scenarioCode, severity, studentId, studentName,
                countryId, dateFrom, dateTo, pageable);
    }

    @GetMapping("/summary")
    @PreAuthorize(MoheSecurityExpressions.ALERT_EVENTS_VIEW)
    public AlertEventCaseSummaryDto getSummary() {
        return caseService.getSummary();
    }

    @GetMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.ALERT_EVENTS_VIEW)
    public AlertEventCaseDto getById(@PathVariable Integer id) {
        return caseService.getById(id);
    }

    @PatchMapping("/{id}/acknowledge")
    @PreAuthorize(MoheSecurityExpressions.ALERT_EVENTS_MANAGE)
    public AlertEventCaseDto acknowledge(@PathVariable Integer id,
                                         @RequestBody(required = false) Map<String, String> body) {
        String notes = body != null ? body.get("notes") : null;
        return caseService.acknowledge(id, notes);
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize(MoheSecurityExpressions.ALERT_EVENTS_MANAGE)
    public AlertEventCaseDto resolve(@PathVariable Integer id,
                                     @RequestBody(required = false) Map<String, String> body) {
        String notes = body != null ? body.get("notes") : null;
        return caseService.resolve(id, notes);
    }

    @PatchMapping("/{id}/dismiss")
    @PreAuthorize(MoheSecurityExpressions.ALERT_EVENTS_MANAGE)
    public AlertEventCaseDto dismiss(@PathVariable Integer id,
                                     @RequestBody(required = false) Map<String, String> body) {
        String notes = body != null ? body.get("notes") : null;
        return caseService.dismiss(id, notes);
    }

    @PostMapping("/run-detection")
    @PreAuthorize("hasAuthority('ALERT_SCENARIOS.MANAGE')")
    public ResponseEntity<Map<String, Object>> runDetection() {
        int count = detectionService.runAllScenarios();
        return ResponseEntity.ok(Map.of("newCasesCreated", count));
    }
}
