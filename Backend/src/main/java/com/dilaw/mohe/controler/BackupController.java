package com.dilaw.mohe.controler;

import com.dilaw.mohe.service.BackupService;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.util.FileUploadValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/v1/backup")
@PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
public class BackupController {

    @Autowired
    private BackupService backupService;

    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createBackup() {
        return ResponseEntity.ok(backupService.createBackup());
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getBackupHistory() {
        return ResponseEntity.ok(backupService.getBackupHistory());
    }

    /**
     * The {@code Content-Disposition} is built by {@link ContentDisposition}, which
     * quotes and encodes the name (RFC 5987 {@code filename*} for non-ASCII); it
     * used to be string concatenation, so a name with a quote or a non-ASCII
     * character produced a malformed header.
     */
    /*
     * The file is the whole database, decrypted. Super admin only, and every
     * download leaves a row in REPORT_ACCESS_LOG (FIX-PLAN ESC, decision D17-b):
     * until then SETTINGS.VIEW was enough, which every country admin holds.
     */
    @GetMapping("/download/{filename}")
    @PreAuthorize(MoheSecurityExpressions.SUPER_ADMIN)
    @com.dilaw.mohe.audit.AuditedRead(resource = "DATABASE_BACKUP", format = "DOWNLOAD")
    public ResponseEntity<Resource> downloadBackup(@PathVariable String filename) {
        Resource resource = backupService.downloadBackup(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }

    /**
     * Restore the database from an uploaded backup.
     *
     * <p>Its own permission, {@code SYSTEM.RESTORE} (created by V065), not the
     * class-level settings gate: a restore replaces every row in the system, and
     * until August 2026 (revision item 1.12) anyone who could edit a setting could
     * do it. The upload is checked here — bare {@code .bak} name, backup or
     * encrypted-backup signature — before the service is called, so a file that is
     * not a backup is a 400 with no SQL run; the service then reads the backup's
     * own header before touching the database.
     */
    @PostMapping("/restore")
    @PreAuthorize(MoheSecurityExpressions.SYSTEM_RESTORE)
    public ResponseEntity<Map<String, Object>> restoreBackup(@RequestParam("file") MultipartFile file) {
        FileUploadValidator.validateBackupUpload(file);
        // S-03: a restore by anyone but the super admin needs a second administrator's
        // signature. The first call raises the SYSTEM_RESTORE request (202); once it
        // is approved the same call, with the same file, within 24 hours, restores.
        long caller = com.dilaw.mohe.config.SecurityRequestUtils.requireUserId();
        String fileName = file.getOriginalFilename();
        if (privilegedChanges.requiresApproval(caller) && !privilegedChanges.restoreApproved(fileName, caller)) {
            try {
                var request = privilegedChanges.requestRestore(fileName, file.getSize(), caller);
                return ResponseEntity.accepted().body(Map.of(
                        "status", "PENDING_APPROVAL",
                        "message", "Restoring replaces every row in the system; a second administrator must approve it. "
                                + "Upload the same file again once request " + request.getId() + " is approved.",
                        "requestId", request.getId()));
            } catch (RuntimeException alreadyPending) {
                return ResponseEntity.accepted().body(Map.of(
                        "status", "PENDING_APPROVAL",
                        "message", "A restore of this file is already waiting for a second administrator."));
            }
        }
        return ResponseEntity.ok(backupService.restoreBackup(file));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.dilaw.mohe.ums.service.PrivilegedChangeService privilegedChanges;

    @DeleteMapping("/{filename}")
    public ResponseEntity<Map<String, Object>> deleteBackup(@PathVariable String filename) {
        backupService.deleteBackup(filename);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Backup deleted successfully"));
    }
}
