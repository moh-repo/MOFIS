package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentAgencyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentAgencyTypeRepository extends JpaRepository<StudentAgencyType, Integer> {

    /** The id the next row should carry; see the entity's id javadoc. */
    @Query(value = "SELECT ISNULL(MAX(STUDENT_AGENCY_TYPE_ID), 0) + 1 FROM MOHECORE.STUDENT_AGENCY_TYPE", nativeQuery = true)
    int nextId();
}
