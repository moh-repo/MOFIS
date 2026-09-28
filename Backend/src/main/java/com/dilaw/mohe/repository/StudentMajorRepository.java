package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentMajor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentMajorRepository extends JpaRepository<StudentMajor, Integer> {

    /** The id the next row should carry; see the entity's id javadoc. */
    @Query(value = "SELECT ISNULL(MAX(STUDENT_MAJOR_ID), 0) + 1 FROM MOHECORE.STUDENT_MAJOR", nativeQuery = true)
    int nextId();
}
