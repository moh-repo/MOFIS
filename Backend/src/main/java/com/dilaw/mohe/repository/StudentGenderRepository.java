package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentGender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentGenderRepository extends JpaRepository<StudentGender, Integer> {

    /** The id the next row should carry; see the entity's id javadoc. */
    @Query(value = "SELECT ISNULL(MAX(STUDENT_GENDER_ID), 0) + 1 FROM MOHECORE.STUDENT_GENDER", nativeQuery = true)
    int nextId();
}
