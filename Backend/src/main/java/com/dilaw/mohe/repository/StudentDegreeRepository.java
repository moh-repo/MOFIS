package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentDegree;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentDegreeRepository extends JpaRepository<StudentDegree, Integer> {

    /** The id the next row should carry; see the entity's id javadoc. */
    @Query(value = "SELECT ISNULL(MAX(STUDENT_DEGREE_ID), 0) + 1 FROM MOHECORE.STUDENT_DEGREE", nativeQuery = true)
    int nextId();
}
