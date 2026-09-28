package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carries out an approved document removal.
 *
 * Separate from the controller because the approval executor performs it long after the
 * request was raised, with no HTTP call in play.
 */
@Service
public class StudentDocumentRemovalService {

    private static final Logger log = LoggerFactory.getLogger(StudentDocumentRemovalService.class);

    private final StudentDocumentService documentService;
    private final StudentDocumentHistoryService historyService;
    private final StudentDataService studentDataService;
    private final PendingOnboardingUploadService uploadService;

    public StudentDocumentRemovalService(StudentDocumentService documentService,
            StudentDocumentHistoryService historyService,
            StudentDataService studentDataService,
            PendingOnboardingUploadService uploadService) {
        this.documentService = documentService;
        this.historyService = historyService;
        this.studentDataService = studentDataService;
        this.uploadService = uploadService;
    }

    /**
     * Moves the file into the student's history folder, records a snapshot, then
     * deactivates the row — so an approved removal stays recoverable.
     */
    @Transactional
    public void remove(Integer documentId, Long changedBy) {
        var docOpt = documentService.getDocumentById(documentId);
        if (docOpt.isEmpty()) {
            log.warn("Document {} no longer exists; nothing to remove", documentId);
            return;
        }
        StudentDocument doc = docOpt.get();

        String historyPath = doc.getFilePath();
        try {
            StudentDatum student = studentDataService.getStudentData(doc.getStudentId());
            historyPath = uploadService.moveToHistory(
                    doc.getFilePath(),
                    doc.getStudentId(),
                    student != null ? student.getStudyCountryIn() : null);
        } catch (Exception e) {
            // Keep going: the row must still be deactivated even if the file could not be moved.
            log.warn("Could not archive file for document {}: {}", documentId, e.getMessage());
        }

        historyService.recordSnapshot(
                doc, historyPath, StudentDocumentHistoryService.ACTION_DELETED, changedBy);
        documentService.deleteDocument(documentId);
        log.info("Removed document {} for student {}", documentId, doc.getStudentId());
    }
}
