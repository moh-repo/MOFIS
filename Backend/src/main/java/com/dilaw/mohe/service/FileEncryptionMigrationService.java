package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * One-time / idempotent migration of existing plaintext uploads to encrypted storage.
 */
@Service
public class FileEncryptionMigrationService {

    private static final Logger log = LoggerFactory.getLogger(FileEncryptionMigrationService.class);

    private final FileEncryptionService fileEncryptionService;
    private final StudentDocumentService studentDocumentService;
    private final StudentFileStorageService studentFileStorage;

    @Value("${file.upload-dir:uploads/}")
    private String uploadDir;

    @Value("${app.backup.directory:C:/MOHE_Backups}")
    private String backupDirectory;

    public FileEncryptionMigrationService(FileEncryptionService fileEncryptionService,
            StudentDocumentService studentDocumentService,
            StudentFileStorageService studentFileStorage) {
        this.fileEncryptionService = fileEncryptionService;
        this.studentDocumentService = studentDocumentService;
        this.studentFileStorage = studentFileStorage;
    }

    @Transactional
    public Map<String, Object> migrateAll() {
        List<String> encrypted = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        Path uploadsRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path backupRoot = Paths.get(backupDirectory).toAbsolutePath().normalize();

        if (Files.exists(uploadsRoot)) {
            encryptTree(uploadsRoot, encrypted, skipped, errors);
        }
        if (Files.exists(backupRoot) && !backupRoot.equals(uploadsRoot)) {
            encryptTree(backupRoot, encrypted, skipped, errors);
        }

        // Paths are resolved per document rather than under one root, since a document
        // may still be recorded in the pre-country-folder layout.
        int documentsUpdated = backfillDocumentMetadata(errors);

        Map<String, Object> result = new HashMap<>();
        result.put("success", errors.isEmpty());
        result.put("filesEncrypted", encrypted.size());
        result.put("filesSkipped", skipped.size());
        result.put("documentsMetadataUpdated", documentsUpdated);
        result.put("encryptedPaths", encrypted);
        result.put("skippedPaths", skipped);
        result.put("errors", errors);
        return result;
    }

    /**
     * S-10 key rotation: rewrites every file written under a key other than the
     * active one. Plaintext files are not touched here (that is {@link #migrateAll}).
     * Safe to re-run; a file already under the active key is skipped.
     */
    public Map<String, Object> rotateAll() {
        List<String> rotated = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int[] alreadyCurrent = {0};
        int[] plaintext = {0};
        Path uploadsRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path backupRoot = Paths.get(backupDirectory).toAbsolutePath().normalize();
        for (Path root : backupRoot.equals(uploadsRoot) ? List.of(uploadsRoot) : List.of(uploadsRoot, backupRoot)) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> !p.getFileName().toString().endsWith(".meta.txt"))
                        .forEach(path -> {
                            try {
                                int keyId = fileEncryptionService.keyIdOf(path);
                                if (keyId < 0) {
                                    plaintext[0]++;
                                } else if (fileEncryptionService.reencryptIfStale(path)) {
                                    rotated.add(root.relativize(path).toString());
                                } else {
                                    alreadyCurrent[0]++;
                                }
                            } catch (Exception e) {
                                log.warn("Failed to rotate {}: {}", path, e.getMessage());
                                errors.add(path + ": " + e.getMessage());
                            }
                        });
            } catch (IOException e) {
                errors.add(root + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", errors.isEmpty());
        result.put("activeKeyId", fileEncryptionService.activeKeyId());
        result.put("filesRotated", rotated.size());
        result.put("filesAlreadyCurrent", alreadyCurrent[0]);
        result.put("filesPlaintext", plaintext[0]);
        result.put("rotatedPaths", rotated);
        result.put("errors", errors);
        log.info("File key rotation: {} rotated, {} already under key {}, {} plaintext, {} errors",
                rotated.size(), alreadyCurrent[0], fileEncryptionService.activeKeyId(), plaintext[0], errors.size());
        return result;
    }

    private void encryptTree(Path root, List<String> encrypted, List<String> skipped, List<String> errors) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().endsWith(".meta.txt"))
                    .forEach(path -> {
                        try {
                            if (fileEncryptionService.isEncrypted(path)) {
                                skipped.add(root.relativize(path).toString());
                                return;
                            }
                            if (fileEncryptionService.encryptFileInPlace(path)) {
                                encrypted.add(root.relativize(path).toString());
                            } else {
                                skipped.add(root.relativize(path).toString());
                            }
                        } catch (Exception e) {
                            log.warn("Failed to encrypt {}: {}", path, e.getMessage());
                            errors.add(path + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            errors.add(root + ": " + e.getMessage());
        }
    }

    private int backfillDocumentMetadata(List<String> errors) {
        int updated = 0;
        for (StudentDocument doc : studentDocumentService.findUnencryptedDocuments()) {
            try {
                Path filePath = studentFileStorage.resolveStored(doc.getFilePath());
                if (!Files.exists(filePath)) {
                    continue;
                }
                byte[] plaintext = fileEncryptionService.readDecrypted(filePath);
                doc.setFileHash(fileEncryptionService.sha256(plaintext));
                doc.setIsEncrypted(fileEncryptionService.isEncrypted(filePath));
                doc.setFileSize((long) plaintext.length);
                if (doc.getVersionNo() == null) {
                    doc.setVersionNo(1);
                }
                studentDocumentService.saveDocument(doc);
                updated++;
            } catch (Exception e) {
                errors.add("document " + doc.getDocumentId() + ": " + e.getMessage());
            }
        }
        return updated;
    }
}
