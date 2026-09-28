package com.dilaw.mohe.audit;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.util.SortWhitelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize(MoheSecurityExpressions.AUDIT)
public class MoheAuditLogController {

    @Autowired
    private MoheAuditLogService auditLogService;

    /**
     * The columns the screen shows, and nothing else. Ordered, so the first entry
     * is the default and a request with no {@code sortBy} still reads newest
     * first. The changed-columns cell is left off deliberately: it holds a JSON
     * array, and ordering by it would order by the text of a serialised list.
     */
    static final Set<String> SORTABLE = new LinkedHashSet<>(List.of(
            "changedAt", "logId", "tableName", "recordId", "operation", "changedBy"));

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) String recordId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String changedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        Sort sort = SortWhitelist.sort(sortBy, sortDir, SORTABLE);
        Page<MoheAuditLog> auditLogs;
        
        if (tableName != null || recordId != null || operation != null || 
            changedBy != null || startDate != null || endDate != null) {
            auditLogs = auditLogService.getAuditLogsWithFilters(
                    tableName, recordId, operation, changedBy, startDate, endDate, page, size, sort);
        } else {
            auditLogs = auditLogService.getAllAuditLogs(page, size, sort);
        }
        
        Map<String, Object> response = new HashMap<>();
        // Views, not entities: the values are masked on the way out (review M1).
        response.put("content", auditLogs.getContent().stream().map(AuditLogView::of).toList());
        response.put("totalElements", auditLogs.getTotalElements());
        response.put("totalPages", auditLogs.getTotalPages());
        response.put("currentPage", auditLogs.getNumber());
        response.put("pageSize", auditLogs.getSize());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogView> getAuditLogById(@PathVariable Long id) {
        MoheAuditLog auditLog = auditLogService.getById(id);
        if (auditLog != null) {
            return ResponseEntity.ok(AuditLogView.of(auditLog));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/table/{tableName}")
    public ResponseEntity<List<AuditLogView>> getAuditLogsByTable(@PathVariable String tableName) {
        return ResponseEntity.ok(views(auditLogService.getAuditLogsByTable(tableName)));
    }

    @GetMapping("/record/{recordId}")
    public ResponseEntity<List<AuditLogView>> getAuditLogsByRecordId(@PathVariable String recordId) {
        return ResponseEntity.ok(views(auditLogService.getAuditLogsByRecordId(recordId)));
    }

    @GetMapping("/operation/{operation}")
    public ResponseEntity<List<AuditLogView>> getAuditLogsByOperation(@PathVariable String operation) {
        return ResponseEntity.ok(views(auditLogService.getAuditLogsByOperation(operation)));
    }

    @GetMapping("/user/{changedBy}")
    public ResponseEntity<List<AuditLogView>> getAuditLogsByUser(@PathVariable String changedBy) {
        return ResponseEntity.ok(views(auditLogService.getAuditLogsByUser(changedBy)));
    }

    private static List<AuditLogView> views(List<MoheAuditLog> logs) {
        return logs.stream().map(AuditLogView::of).toList();
    }

    @GetMapping("/metadata/tables")
    public ResponseEntity<List<String>> getDistinctTableNames() {
        return ResponseEntity.ok(auditLogService.getDistinctTableNames());
    }

    @GetMapping("/metadata/users")
    public ResponseEntity<List<String>> getDistinctChangedBy() {
        return ResponseEntity.ok(auditLogService.getDistinctChangedBy());
    }
}
