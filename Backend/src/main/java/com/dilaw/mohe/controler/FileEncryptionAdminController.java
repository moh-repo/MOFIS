package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.service.FileEncryptionMigrationService;
import com.dilaw.mohe.service.StudentDocumentCountryMigrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("api/v1/admin")
@PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
public class FileEncryptionAdminController {

    @Autowired
    private FileEncryptionMigrationService migrationService;

    @Autowired
    private StudentDocumentCountryMigrationService countryMigrationService;

    /**
     * Idempotent job: encrypt existing plaintext uploads and backfill document hashes.
     */
    @PostMapping("/encrypt-existing-files")
    public ResponseEntity<Map<String, Object>> encryptExistingFiles() {
        return ResponseEntity.ok(migrationService.migrateAll());
    }

    /**
     * S-10: after a new key was added to MOHE_FILE_ENCRYPTION_KEYS and made active,
     * rewrites every file still under an older key. Idempotent.
     */
    @PostMapping("/rotate-file-keys")
    public ResponseEntity<Map<String, Object>> rotateFileKeys() {
        return ResponseEntity.ok(migrationService.rotateAll());
    }

    /**
     * Idempotent job: move student uploads that predate country folders into
     * {@code uploads/countries/country_{studyCountryIn}/students/documents/} and repoint
     * the stored paths. Safe to re-run — an already-migrated student is skipped.
     */
    @PostMapping("/migrate-documents-to-country-folders")
    public ResponseEntity<Map<String, Object>> migrateDocumentsToCountryFolders() {
        return ResponseEntity.ok(countryMigrationService.migrateAll());
    }
}
