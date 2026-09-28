package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyMajorIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StudyMajorInRepository extends JpaRepository<StudyMajorIn, Integer> {
    List<StudyMajorIn> findByStudyEductionId(Integer studyEductionId);

    /** The id the next row should carry; see the entity's id javadoc. */
    @Query(value = "SELECT ISNULL(MAX(STUDY_MAJOR_ID), 0) + 1 FROM MOHECORE.STUDY_MAJOR_IN", nativeQuery = true)
    int nextId();
}
