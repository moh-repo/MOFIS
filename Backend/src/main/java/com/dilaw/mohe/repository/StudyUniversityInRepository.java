package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyUniversityIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyUniversityInRepository extends JpaRepository<StudyUniversityIn, Integer> {

    /** The id the next row should carry; see the entity's id javadoc. */
    @Query(value = "SELECT ISNULL(MAX(STUDY_UNIVERSITY_IN_ID), 0) + 1 FROM MOHECORE.STUDY_UNIVERSITY_IN", nativeQuery = true)
    int nextId();
}
