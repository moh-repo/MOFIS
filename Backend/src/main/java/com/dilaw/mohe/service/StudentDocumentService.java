package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDocument;
import com.dilaw.mohe.repository.StudentDocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudentDocumentService {

    @Autowired
    private StudentDocumentRepository studentDocumentRepository;

    public List<StudentDocument> getDocumentsByStudentId(Integer studentId) {
        return studentDocumentRepository.findActiveDocumentsByStudentId(studentId);
    }

    /**
     * Writes a document row, new or existing.
     *
     * <p>{@code @Transactional} is not decoration here. The class default is
     * {@code readOnly = true}, under which Hibernate sets MANUAL flush and never writes
     * a dirty entity at commit. New rows still appeared — an IDENTITY key forces the
     * INSERT immediately — so uploads worked while every UPDATE was silently discarded:
     * renaming a document, changing its type or expiry, and replacing its file all
     * returned 200 with the new values and left the row untouched. A replace was worse
     * than a no-op, because the file really did move to the history folder while the row
     * went on pointing at the path it had left.
     */
    @Transactional
    public StudentDocument saveDocument(StudentDocument document) {
        return studentDocumentRepository.save(document);
    }

    public Optional<StudentDocument> getDocumentById(Integer documentId) {
        return studentDocumentRepository.findById(documentId);
    }

    @Transactional
    public void deleteDocument(Integer documentId) {
        Optional<StudentDocument> document = studentDocumentRepository.findById(documentId);
        if (document.isPresent()) {
            StudentDocument doc = document.get();
            doc.setIsActive(false);
            studentDocumentRepository.save(doc);
        }
    }

    public List<StudentDocument> getDocumentsByType(String documentType) {
        return studentDocumentRepository.findByDocumentType(documentType);
    }

    public List<StudentDocument> getDocumentsByStudentIdAndType(Integer studentId, String documentType) {
        return studentDocumentRepository.findByStudentIdAndDocumentType(studentId, documentType);
    }

    @Transactional
    public void permanentlyDeleteDocument(Integer documentId) {
        studentDocumentRepository.deleteById(documentId);
    }

    public Optional<StudentDocument> findByFilePath(String filePath) {
        return studentDocumentRepository.findByFilePath(filePath);
    }

    public List<StudentDocument> findUnencryptedDocuments() {
        return studentDocumentRepository.findAllByIsEncryptedFalse();
    }
}
