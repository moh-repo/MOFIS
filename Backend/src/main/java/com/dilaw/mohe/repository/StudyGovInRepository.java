package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyGovIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StudyGovInRepository extends JpaRepository<StudyGovIn, Integer> {
    List<StudyGovIn> findByStudyCountryInId(Integer studyCountryInId);

    /** The id the next row should carry; see the entity's id javadoc. */
    @Query(value = "SELECT ISNULL(MAX(STUDY_GOV_IN_ID), 0) + 1 FROM MOHECORE.STUDY_GOV_IN", nativeQuery = true)
    int nextId();
}
