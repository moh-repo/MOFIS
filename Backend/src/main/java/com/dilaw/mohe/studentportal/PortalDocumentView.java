package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.model.StudentDocument;

import java.time.Instant;

/**
 * One of the student's documents as the portal lists it (live QA L12).
 *
 * <p>The list used to return the {@link StudentDocument} entity itself, so every
 * student's browser received the file's path in the server's document store and its
 * SHA-256 hash, neither of which a student needs or should see. This is what the
 * Documents page shows and downloads by, and nothing else.
 */
public record PortalDocumentView(
        Integer documentId,
        Integer studentId,
        String documentName,
        String documentType,
        String originalFilename,
        Long fileSize,
        Instant uploadDate) {

    public static PortalDocumentView of(StudentDocument d) {
        return new PortalDocumentView(d.getDocumentId(), d.getStudentId(), d.getDocumentName(),
                d.getDocumentType(), d.getOriginalFilename(), d.getFileSize(), d.getUploadDate());
    }
}
