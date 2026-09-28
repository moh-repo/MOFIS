package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentCurrentLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentCurrentLevelRepository extends JpaRepository<StudentCurrentLevel, Integer> {

    /** The id the next row should carry; see the entity's id javadoc. */
    @Query(value = "SELECT ISNULL(MAX(STUDENT_CURRENT_LEVEL_ID), 0) + 1 FROM MOHECORE.STUDENT_CURRENT_LEVEL", nativeQuery = true)
    int nextId();
}
