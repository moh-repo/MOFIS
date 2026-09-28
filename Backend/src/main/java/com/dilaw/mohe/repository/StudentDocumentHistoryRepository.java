package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentDocumentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentDocumentHistoryRepository extends JpaRepository<StudentDocumentHistory, Integer> {

    List<StudentDocumentHistory> findByDocumentIdOrderByVersionNoDesc(Integer documentId);

    Optional<StudentDocumentHistory> findByHistoryIdAndDocumentId(Integer historyId, Integer documentId);

    /** All archived versions for a student (replaced files and removed documents), newest first. */
    List<StudentDocumentHistory> findByStudentIdOrderByChangedAtDesc(Integer studentId);
}
