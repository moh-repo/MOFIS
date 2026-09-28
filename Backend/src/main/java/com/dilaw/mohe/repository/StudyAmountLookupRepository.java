package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyAmountLookup;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudyAmountLookupRepository
        extends JpaRepository<StudyAmountLookup, Integer>, JpaSpecificationExecutor<StudyAmountLookup> {

    /**
     * The finance amount in force for one attaché, degree and agency type.
     *
     * <p>The attaché's own row if it has set one, otherwise the shared default.
     * The {@code ORDER BY} is the override — see
     * {@link StudyYearsLookupRepository#resolve} for the reasoning, which is the
     * same here.
     */
    @Query("""
            SELECT a FROM StudyAmountLookup a
             WHERE a.studentDegreeId = :degreeId
               AND a.studentAgencyTypeId = :agencyTypeId
               AND (a.studyCountryInId = :countryId OR a.studyCountryInId IS NULL)
             ORDER BY CASE WHEN a.studyCountryInId IS NULL THEN 1 ELSE 0 END
            """)
    List<StudyAmountLookup> resolve(@Param("degreeId") Integer degreeId,
                                    @Param("agencyTypeId") Integer agencyTypeId,
                                    @Param("countryId") Integer countryId,
                                    Pageable pageable);

    /** The shared default alone, for a caller with no country to resolve against. */
    @Query("""
            SELECT a FROM StudyAmountLookup a
             WHERE a.studentDegreeId = :degreeId
               AND a.studentAgencyTypeId = :agencyTypeId
               AND a.studyCountryInId IS NULL
            """)
    List<StudyAmountLookup> resolveDefault(@Param("degreeId") Integer degreeId,
                                           @Param("agencyTypeId") Integer agencyTypeId);
}
