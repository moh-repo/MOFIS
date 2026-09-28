package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentCountry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentCountryRepository  extends JpaRepository<StudentCountry, Integer> {

    /** The id the next row should carry; see the entity's id javadoc. */
    @Query(value = "SELECT ISNULL(MAX(STUDENT_COUNTRY_ID), 0) + 1 FROM MOHECORE.STUDENT_COUNTRY", nativeQuery = true)
    int nextId();
}
