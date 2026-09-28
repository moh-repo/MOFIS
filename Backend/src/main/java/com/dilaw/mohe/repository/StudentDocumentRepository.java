package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentDocumentRepository extends JpaRepository<StudentDocument, Integer> {
    
    List<StudentDocument> findByStudentIdAndIsActiveTrue(Integer studentId);

    /**
     * Every document row for a student, removed ones included — their files are still on
     * disk and have to move with the rest when the student changes country.
     */
    List<StudentDocument> findByStudentId(Integer studentId);
    
    @Query("SELECT d FROM StudentDocument d WHERE d.studentId = :studentId AND d.isActive = true")
    List<StudentDocument> findActiveDocumentsByStudentId(@Param("studentId") Integer studentId);
    
    @Query("SELECT d FROM StudentDocument d WHERE d.documentType = :documentType AND d.isActive = true")
    List<StudentDocument> findByDocumentType(@Param("documentType") String documentType);
    
    @Query("SELECT d FROM StudentDocument d WHERE d.studentId = :studentId AND d.documentType = :documentType AND d.isActive = true")
    List<StudentDocument> findByStudentIdAndDocumentType(@Param("studentId") Integer studentId, @Param("documentType") String documentType);

    /** Documents expiring between today and the given threshold date. */
    @Query("SELECT d FROM StudentDocument d WHERE d.isActive = true AND d.expiryDate IS NOT NULL AND d.expiryDate BETWEEN :today AND :threshold")
    List<StudentDocument> findExpiringBetween(@Param("today") LocalDate today, @Param("threshold") LocalDate threshold);

    /**
     * Documents expiring on a day, with the student they belong to, for the
     * topbar calendar's agenda.
     *
     * <p>Joined to {@code StudentDatum} for two reasons: the agenda names the
     * student, and {@code STUDENT_DOCUMENT} carries no country of its own, so the
     * study country is the only thing a country officer's scope can be applied
     * to. {@code findExpiringBetween} above is deliberately unscoped — the
     * nightly expiry job runs as the system and must see every country.
     *
     * <p>A ministry user passes {@code ministry = true} and the id list is
     * ignored; a country user must send a non-empty list, since {@code IN ()} is
     * not valid SQL.
     */
    @Query("""
            SELECT d, s.studentName
            FROM StudentDocument d
            JOIN StudentDatum s ON s.studentId = d.studentId
            WHERE d.isActive = true
              AND d.expiryDate BETWEEN :from AND :to
              AND (:ministry = true OR s.studyCountryIn IN :allowedIds)
            ORDER BY d.expiryDate, d.documentName
            """)
    List<Object[]> findExpiringInScope(@Param("from") LocalDate from,
                                       @Param("to") LocalDate to,
                                       @Param("ministry") boolean ministry,
                                       @Param("allowedIds") List<Integer> allowedIds);

    Optional<StudentDocument> findByFilePath(String filePath);

    List<StudentDocument> findAllByIsEncryptedFalse();

    /**
     * How many student documents claim this type.
     *
     * <p>By name, not by id: {@code STUDENT_DOCUMENTS.DOCUMENT_TYPE} stores the
     * type's name as text and there is no foreign key to {@code DOCUMENT_TYPES},
     * so this is what stands in for one when a type is about to be deleted.
     */
    long countByDocumentType(String documentType);
}
