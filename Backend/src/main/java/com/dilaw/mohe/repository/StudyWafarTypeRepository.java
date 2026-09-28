package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyWafarType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyWafarTypeRepository extends JpaRepository<StudyWafarType, Integer> {

    /** The id the next row should carry; see the entity's id javadoc. */
    @Query(value = "SELECT ISNULL(MAX(WAFAR_TYPE_ID), 0) + 1 FROM MOHECORE.STUDY_WAFAR_TYPE", nativeQuery = true)
    int nextId();
}
