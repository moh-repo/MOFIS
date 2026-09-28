package com.dilaw.mohe.retention;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.ums.service.UmsAuditLogService;
import com.dilaw.mohe.config.SecurityRequestUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S-12: the dry-run report on demand, and the audited switch that applies it.
 */
@RestController
@RequestMapping("api/v1/admin/retention")
@PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
public class DataRetentionController {

    private final DataRetentionJob job;
    private final UmsAuditLogService auditLog;
    private final String reportDir;

    public DataRetentionController(DataRetentionJob job, UmsAuditLogService auditLog,
                                   @org.springframework.beans.factory.annotation.Value("${mohe.retention.report-dir:archive/retention}") String reportDir) {
        this.job = job;
        this.auditLog = auditLog;
        this.reportDir = reportDir;
    }

    /** The schedule in force and what a run now would touch; nothing is changed. */
    @GetMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview() {
        DataRetentionJob.Report report = job.preview(LocalDateTime.now());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schedule", job.schedule());
        body.put("report", report);
        return ResponseEntity.ok(body);
    }

    /** Applies the schedule now, whatever MOHE_RETENTION_DRY_RUN says. Audited. */
    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> apply() {
        Long userId = SecurityRequestUtils.currentUserId().orElse(null);
        DataRetentionJob.Report report = job.apply(LocalDateTime.now());
        Path file = job.write(report, Paths.get(reportDir));
        auditLog.log(userId, "DATA_RETENTION_APPLIED", "Retention", null, null,
                "affected=" + report.totalAffected() + ",students=" + report.studentIds().size()
                        + (file == null ? "" : ",report=" + file.getFileName()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("report", report);
        body.put("reportFile", file == null ? null : file.toString());
        return ResponseEntity.ok(body);
    }
}
