package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentMinistryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentMinistryTypeRepository extends JpaRepository<StudentMinistryType, Integer> {

    /** The id the next row should carry; see the entity's id javadoc. */
    @Query(value = "SELECT ISNULL(MAX(STUDENT_MINISTRY_TYPE_ID), 0) + 1 FROM MOHECORE.STUDENT_MINISTRY_TYPE", nativeQuery = true)
    int nextId();
}
