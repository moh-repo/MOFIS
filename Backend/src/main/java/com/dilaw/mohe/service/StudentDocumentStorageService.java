package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Writes an uploaded file into a student's folder, encrypted, and records the
 * STUDENT_DOCUMENTS row; reads one back.
 *
 * <p>Was a private method of StudentDataController. Travel tickets store their
 * PDF the same way (review T3), from a controller that must not need the
 * student-editing permissions that one carries, so it lives here now.
 */
@Service
public class StudentDocumentStorageService {

    @Autowired private StudentFileStorageService studentFileStorage;
    @Autowired private FileEncryptionService fileEncryptionService;
    @Autowired private StudentDocumentService studentDocumentService;

    /**
     * Encrypts the upload into the student's folder and persists the row.
     * DOCUMENT_TYPE is the business classification (passport, certificate …) and
     * stays null when the uploader chose none: it is not defaulted to the file's
     * extension, which is derived from the filename where it is needed.
     */
    public StudentDocument store(Integer studentId, Integer studyCountryIn, MultipartFile file,
                                 String documentName, String documentType) throws IOException {
        String origName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String timestampedName = PendingOnboardingUploadService.buildTimestampedFilename(origName);

        Path destDir = studentFileStorage.studentDir(studentId, studyCountryIn).resolve(datePart);
        Files.createDirectories(destDir);
        Path destFile = destDir.resolve(timestampedName);
        fileEncryptionService.encryptMultipartFile(file, destFile);
        String relPath = studentFileStorage.relativise(destFile);

        byte[] plaintext = fileEncryptionService.readDecrypted(destFile);
        StudentDocument doc = new StudentDocument(
                studentId,
                documentName != null && !documentName.isBlank() ? documentName.trim() : origName,
                documentType != null && !documentType.isBlank() ? documentType.trim() : null,
                relPath,
                origName,
                (long) plaintext.length);
        doc.setFileHash(fileEncryptionService.sha256(plaintext));
        doc.setIsEncrypted(true);
        doc.setVersionNo(1);
        return studentDocumentService.saveDocument(doc);
    }

    /** The document's decrypted content, or null when its file is not on disk. */
    public byte[] read(StudentDocument doc) throws IOException {
        Path filePath = studentFileStorage.resolveStored(doc.getFilePath());
        if (!Files.exists(filePath)) {
            return null;
        }
        return fileEncryptionService.readDecrypted(filePath);
    }
}
