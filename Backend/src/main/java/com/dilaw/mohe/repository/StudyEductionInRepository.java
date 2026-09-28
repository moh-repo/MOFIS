package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyEductionIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudyEductionInRepository extends JpaRepository<StudyEductionIn, Integer> {
    List<StudyEductionIn> findByStudyCollegeInId(Integer studyCollegeInId);

    /** The id the next row should carry; see the entity's id javadoc. */
    @Query(value = "SELECT ISNULL(MAX(STUDY_EDUCTION_ID), 0) + 1 FROM MOHECORE.STUDY_EDUCTION_IN", nativeQuery = true)
    int nextId();
}
