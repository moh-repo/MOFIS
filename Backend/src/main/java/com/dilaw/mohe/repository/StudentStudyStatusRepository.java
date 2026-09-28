package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentStudyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentStudyStatusRepository extends JpaRepository<StudentStudyStatus, Integer> {

    java.util.Optional<StudentStudyStatus> findFirstByStudentStudyStatusNameEnIgnoreCase(String nameEn);

    /** Resolve a status by its stable CODE (V028) rather than by ID or display name. */
    java.util.Optional<StudentStudyStatus> findFirstByCodeIgnoreCase(String code);

    /** The id the next row should carry; see the entity's id javadoc. */
    @Query(value = "SELECT ISNULL(MAX(STUDENT_STUDY_STATUS_ID), 0) + 1 FROM MOHECORE.STUDENT_STUDY_STATUS", nativeQuery = true)
    int nextId();
}
