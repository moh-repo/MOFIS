package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.EnrollmentStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnrollmentStatisticsRepository extends JpaRepository<EnrollmentStatistics, EnrollmentStatistics.EnrollmentStatisticsId> {

    @Query("SELECT DISTINCT e.enrollmentQuarter FROM EnrollmentStatistics e WHERE "
            + "(:applyCountryFilter = false OR e.studyCountryInId IN :countryIds) "
            + "ORDER BY e.enrollmentQuarter DESC")
    List<Integer> findDistinctQuarters(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    /*
     * Filter options are read from the view rather than from the lookup tables.
     *
     * The dropdowns used to be filled from STUDENT_MAJOR, STUDENT_DEGREE,
     * STUDENT_STUDY_TYPE and STUDY_COUNTRY_IN in full — 444 majors against the
     * two that appear in the view — so almost every choice could only return
     * "no data". Country was worse than useless: the view's STUDENT_COUNTRY_ID
     * is the student's nationality (STUDENT_COUNTRY), while the dropdown listed
     * study destinations (STUDY_COUNTRY_IN), whose ids overlap. Selecting Egypt
     * (STUDY_COUNTRY_IN 1) silently matched Yemen (STUDENT_COUNTRY 1).
     *
     * Taking each option straight from the view's own id/name pair makes every
     * option correspond to rows that exist, and removes the lookup mismatch.
     */

    @Query("SELECT DISTINCT e.studentCountryId, e.studentCountryName, e.studentCountryNameEn FROM EnrollmentStatistics e WHERE "
            + "(:applyCountryFilter = false OR e.studyCountryInId IN :countryIds) "
            + "AND e.studentCountryId IS NOT NULL")
    List<Object[]> findDistinctCountries(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT DISTINCT e.studentMajorId, e.studentMajorName, e.studentMajorNameEn FROM EnrollmentStatistics e WHERE "
            + "(:applyCountryFilter = false OR e.studyCountryInId IN :countryIds) "
            + "AND e.studentMajorId IS NOT NULL")
    List<Object[]> findDistinctMajors(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT DISTINCT e.studentDegreeId, e.studentDegreeName, e.studentDegreeNameEn FROM EnrollmentStatistics e WHERE "
            + "(:applyCountryFilter = false OR e.studyCountryInId IN :countryIds) "
            + "AND e.studentDegreeId IS NOT NULL")
    List<Object[]> findDistinctDegrees(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT DISTINCT e.studentStudyTypeId, e.studentStudyTypeName, e.studentStudyTypeNameEn FROM EnrollmentStatistics e WHERE "
            + "(:applyCountryFilter = false OR e.studyCountryInId IN :countryIds) "
            + "AND e.studentStudyTypeId IS NOT NULL")
    List<Object[]> findDistinctStudyTypes(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT e FROM EnrollmentStatistics e WHERE " +
            "(:quarter IS NULL OR e.enrollmentQuarter = :quarter) AND " +
            "(:applyCountryFilter = false OR e.studyCountryInId IN :countryIds) AND " +
            "(:majorId IS NULL OR e.studentMajorId = :majorId) AND " +
            "(:degreeId IS NULL OR e.studentDegreeId = :degreeId) AND " +
            "(:studyTypeId IS NULL OR e.studentStudyTypeId = :studyTypeId)")
    List<EnrollmentStatistics> search(
            @Param("quarter") Integer quarter,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds,
            @Param("majorId") Integer majorId,
            @Param("degreeId") Integer degreeId,
            @Param("studyTypeId") Integer studyTypeId
    );
}
