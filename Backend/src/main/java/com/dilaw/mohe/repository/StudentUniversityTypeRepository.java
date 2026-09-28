package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentUniversityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentUniversityTypeRepository extends JpaRepository<StudentUniversityType, Integer> {

    /** The id the next row should carry; see the entity's id javadoc. */
    @Query(value = "SELECT ISNULL(MAX(STUDENT_UNIVERSITY_TYPE_ID), 0) + 1 FROM MOHECORE.STUDENT_UNIVERSITY_TYPE", nativeQuery = true)
    int nextId();
}
