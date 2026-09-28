package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDocument;
import com.dilaw.mohe.model.StudentDocumentHistory;
import com.dilaw.mohe.repository.StudentDocumentHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudentDocumentHistoryService {

    public static final String ACTION_REPLACED = "REPLACED";
    public static final String ACTION_DELETED = "DELETED";

    @Autowired
    private StudentDocumentHistoryRepository historyRepository;

    public List<StudentDocumentHistory> getHistoryByDocumentId(Integer documentId) {
        return historyRepository.findByDocumentIdOrderByVersionNoDesc(documentId);
    }

    /** Archived versions for a whole student — replaced files and removed documents. */
    public List<StudentDocumentHistory> getHistoryByStudentId(Integer studentId) {
        return historyRepository.findByStudentIdOrderByChangedAtDesc(studentId);
    }

    public Optional<StudentDocumentHistory> getHistoryById(Integer historyId) {
        return historyRepository.findById(historyId);
    }

    @Transactional
    public StudentDocumentHistory recordSnapshot(StudentDocument doc, String historyFilePath, String action,
            Long changedBy) {
        StudentDocumentHistory history = new StudentDocumentHistory();
        history.setDocumentId(doc.getDocumentId());
        history.setStudentId(doc.getStudentId());
        history.setDocumentName(doc.getDocumentName());
        history.setDocumentType(doc.getDocumentType());
        history.setFilePath(historyFilePath);
        history.setOriginalFilename(doc.getOriginalFilename());
        history.setFileSize(doc.getFileSize());
        history.setFileHash(doc.getFileHash());
        history.setVersionNo(doc.getVersionNo() != null ? doc.getVersionNo() : 1);
        history.setAction(action);
        history.setChangedBy(changedBy);
        history.setChangedAt(Instant.now());
        return historyRepository.save(history);
    }
}
