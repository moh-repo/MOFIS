package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stages student profile/document uploads until STUDENT_DATA approval INSERT executes.
 */
@Transactional(readOnly = true)
@Service
public class PendingOnboardingUploadService {

    private final Path pendingRoot = Paths.get("uploads/pending-onboarding").toAbsolutePath().normalize();
    private final FileEncryptionService fileEncryptionService;
    private final StudentFileStorageService studentFileStorage;

    public PendingOnboardingUploadService(FileEncryptionService fileEncryptionService,
            StudentFileStorageService studentFileStorage) {
        this.fileEncryptionService = fileEncryptionService;
        this.studentFileStorage = studentFileStorage;
        try {
            Files.createDirectories(pendingRoot);
        } catch (IOException e) {
            throw new RuntimeException("Could not create pending onboarding dirs", e);
        }
    }

    /**
     * @return generated session ID if anything was saved, otherwise null.
     */
    public String stageFiles(MultipartFile profilePicture, List<MultipartFile> documents) throws IOException {
        return stageFiles(profilePicture, documents, null);
    }

    /**
     * @param documentTypes optional business classifications (passport, certificate, …),
     *                      positionally aligned with {@code documents}. A blank or missing
     *                      entry leaves DOCUMENT_TYPE null — the file format is derived from
     *                      the filename and is never stored here.
     */
    public String stageFiles(MultipartFile profilePicture, List<MultipartFile> documents,
            List<String> documentTypes) throws IOException {
        boolean hasDocs = documents != null && documents.stream().anyMatch(f -> f != null && !f.isEmpty());
        if ((profilePicture == null || profilePicture.isEmpty()) && !hasDocs) {
            return null;
        }
        String sessionId = UUID.randomUUID().toString();
        Path dir = pendingRoot.resolve(sessionId);
        Files.createDirectories(dir);
        if (profilePicture != null && !profilePicture.isEmpty()) {
            String orig = profilePicture.getOriginalFilename();
            Path target = dir.resolve("profile_" + UUID.randomUUID() + suffixExt(orig));
            fileEncryptionService.encryptMultipartFile(profilePicture, target);
        }
        int i = 0;
        if (documents != null) {
            for (int idx = 0; idx < documents.size(); idx++) {
                MultipartFile mf = documents.get(idx);
                if (mf == null || mf.isEmpty()) {
                    continue;
                }
                String orig = mf.getOriginalFilename();
                Path target = dir.resolve("doc_" + (i++) + "_" + UUID.randomUUID() + suffixExt(orig));
                fileEncryptionService.encryptMultipartFile(mf, target);
                String type = documentTypes != null && idx < documentTypes.size() ? documentTypes.get(idx) : null;
                // Line 1: original filename, line 2 (optional): selected document type.
                Path meta = target.resolveSibling(target.getFileName().toString() + ".meta.txt");
                Files.writeString(meta, (orig != null ? orig : "document")
                        + (type != null && !type.isBlank() ? "\n" + type.trim() : ""));
            }
        }
        return sessionId;
    }

    /**
     * @param studyCountryIn the student's STUDY_COUNTRY_IN, which decides the country
     *                       folder the staged files are promoted into. Null is allowed
     *                       and lands them under {@code countries/_unassigned}.
     */
    public void promoteToStudent(String sessionId, Integer studentId, Integer studyCountryIn,
            StudentDatumUpdate picturePathSaver, StudentDocumentService documentService) throws IOException {
        if (sessionId == null || sessionId.isBlank() || studentId == null) {
            return;
        }
        Path src = pendingRoot.resolve(sessionId.strip());
        if (!Files.isDirectory(src)) {
            return;
        }

        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Path studentDir = studentFileStorage.studentDir(studentId, studyCountryIn);
        Path dateDir = studentDir.resolve(datePart);
        Files.createDirectories(dateDir);

        try (Stream<Path> stream = Files.list(src)) {
            List<Path> toMove = stream
                    .filter(p -> Files.isRegularFile(p) && !p.getFileName().toString().endsWith(".meta.txt"))
                    .collect(Collectors.toList());
            for (Path p : toMove) {
                String stagingFilename = p.getFileName().toString();
                Path metaPending = Paths.get(p.toString() + ".meta.txt");
                String metaOriginalName = stagingFilename;
                String metaDocumentType = null;
                if (Files.exists(metaPending)) {
                    String[] metaLines = Files.readString(metaPending).split("\\R", 2);
                    metaOriginalName = metaLines[0].trim();
                    if (metaLines.length > 1 && !metaLines[1].isBlank()) {
                        metaDocumentType = metaLines[1].trim();
                    }
                }

                String timestampedFilename = buildTimestampedFilename(metaOriginalName);
                Path dest = dateDir.resolve(timestampedFilename);

                Files.move(p, dest, StandardCopyOption.REPLACE_EXISTING);
                Path metaDest = Paths.get(dest.toString() + ".meta.txt");
                if (Files.exists(metaPending)) {
                    Files.move(metaPending, metaDest, StandardCopyOption.REPLACE_EXISTING);
                }

                String relStored = studentFileStorage.relativise(dest);
                if (stagingFilename.startsWith("profile_")) {
                    picturePathSaver.setPicturePath(studentId, relStored);
                }
                if (stagingFilename.startsWith("doc_")) {
                    byte[] plaintext = fileEncryptionService.readDecrypted(dest);
                    long size = plaintext.length;
                    StudentDocument doc = new StudentDocument(
                            studentId,
                            metaOriginalName,
                            metaDocumentType,
                            relStored,
                            metaOriginalName,
                            size);
                    doc.setFileHash(fileEncryptionService.sha256(plaintext));
                    doc.setIsEncrypted(true);
                    doc.setVersionNo(1);
                    documentService.saveDocument(doc);
                    Files.deleteIfExists(metaDest);
                }
            }
        }
        deleteDirectoryRecursive(src);
    }

    /**
     * Move a document file to the student's history subfolder and return the new path,
     * relative to the uploads root.
     *
     * <p>The source is resolved through {@link StudentFileStorageService#resolveStored},
     * so a file still recorded under the pre-migration layout is archived correctly and
     * lands in the country folder along with everything else.
     */
    public String moveToHistory(String relFilePath, Integer studentId, Integer studyCountryIn)
            throws IOException {
        Path srcFile = studentFileStorage.resolveStored(relFilePath);
        if (!Files.exists(srcFile)) {
            return relFilePath;
        }
        Path historyDir = studentFileStorage.studentDir(studentId, studyCountryIn).resolve("history");
        Files.createDirectories(historyDir);
        String historyFilename = buildHistoryFilename(srcFile.getFileName().toString());
        Path dest = historyDir.resolve(historyFilename);
        Files.move(srcFile, dest, StandardCopyOption.REPLACE_EXISTING);
        return studentFileStorage.relativise(dest);
    }

    public static String buildTimestampedFilename(String originalName) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmssSSS"));
        if (originalName == null || !originalName.contains(".")) {
            return (originalName != null ? originalName : "file") + "_" + timestamp;
        }
        int dotIdx = originalName.lastIndexOf(".");
        String base = originalName.substring(0, dotIdx);
        String ext = originalName.substring(dotIdx);
        return base + "_" + timestamp + ext;
    }

    private static String buildHistoryFilename(String originalFilename) {
        String timestamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "_" + LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmssSSS"));
        return timestamp + "_" + originalFilename;
    }

    private void deleteDirectoryRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private static String suffixExt(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    @FunctionalInterface
    public interface StudentDatumUpdate {
        void setPicturePath(Integer studentId, String picturePathRelativeToDocumentsRoot);
    }
}
