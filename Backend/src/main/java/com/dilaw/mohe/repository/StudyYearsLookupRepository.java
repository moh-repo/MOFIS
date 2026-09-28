package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyYearsLookup;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudyYearsLookupRepository
        extends JpaRepository<StudyYearsLookup, Integer>, JpaSpecificationExecutor<StudyYearsLookup> {

    /**
     * The number of study years in force for one attaché, degree and major.
     *
     * <p>The attaché's own row if it has set one, otherwise the shared default —
     * and the {@code ORDER BY} <em>is</em> the override: rows with a country sort
     * before the null one, so the first result is the more specific answer. One
     * query rather than a lookup-then-fallback pair, which would read the table
     * twice on the common path where no override exists.
     *
     * <p>Returns a list so the caller can take the first; combine with
     * {@code Pageable.ofSize(1)}. The unique constraint means at most two rows can
     * match anyway.
     */
    @Query("""
            SELECT y FROM StudyYearsLookup y
             WHERE y.studentDegreeId = :degreeId
               AND y.studentMajorId = :majorId
               AND (y.studyCountryInId = :countryId OR y.studyCountryInId IS NULL)
             ORDER BY CASE WHEN y.studyCountryInId IS NULL THEN 1 ELSE 0 END
            """)
    List<StudyYearsLookup> resolve(@Param("degreeId") Integer degreeId,
                                   @Param("majorId") Integer majorId,
                                   @Param("countryId") Integer countryId,
                                   Pageable pageable);

    /**
     * The shared default alone, for a caller with no country to resolve against.
     *
     * <p>Separate from {@link #resolve} because {@code y.studyCountryInId = null}
     * is never true in JPQL — passing a null countryId to the query above would
     * silently return only the default anyway, but relying on that reads as a bug
     * to the next person. This says what it means.
     */
    @Query("""
            SELECT y FROM StudyYearsLookup y
             WHERE y.studentDegreeId = :degreeId
               AND y.studentMajorId = :majorId
               AND y.studyCountryInId IS NULL
            """)
    List<StudyYearsLookup> resolveDefault(@Param("degreeId") Integer degreeId,
                                          @Param("majorId") Integer majorId);
}
