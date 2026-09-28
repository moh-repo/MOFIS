package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentStudyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentStudyTypeRepository extends JpaRepository<StudentStudyType, Integer> {

    /** The id the next row should carry; see the entity's id javadoc. */
    @Query(value = "SELECT ISNULL(MAX(STUDENT_STUDY_TYPE_ID), 0) + 1 FROM MOHECORE.STUDENT_STUDY_TYPE", nativeQuery = true)
    int nextId();
}
