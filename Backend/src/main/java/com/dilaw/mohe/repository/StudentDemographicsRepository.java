package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentDemographics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentDemographicsRepository extends JpaRepository<StudentDemographics, Integer> {
    
    @Query("SELECT d FROM StudentDemographics d WHERE " +
           "(:applyCountryFilter = false OR d.studyCountryInId IN :countryIds) AND " +
           "(:genderId IS NULL OR d.studentGenderName = (SELECT g.studentGenderName FROM StudentGender g WHERE g.id = :genderId)) AND " +
           "(:levelId IS NULL OR d.studentCurrentLevelId = :levelId) AND " +
           "(:studyTypeId IS NULL OR d.studentStudyTypeId = :studyTypeId) AND " +
           "(:degreeId IS NULL OR d.studentDegreeId = :degreeId) AND " +
           "(:majorId IS NULL OR d.studentMajorId = :majorId) AND " +
           "(:statusId IS NULL OR d.studentStudyStatusId = :statusId) AND " +
           "(:ageRange IS NULL OR " +
                "CASE " +
                "WHEN d.studentAge IS NULL THEN NULL " +
                "WHEN d.studentAge < 20 THEN 'UNDER_20' " +
                    "WHEN d.studentAge BETWEEN 20 AND 24 THEN '20_24' " +
                    "WHEN d.studentAge BETWEEN 25 AND 29 THEN '25_29' " +
                    "WHEN d.studentAge BETWEEN 30 AND 34 THEN '30_34' " +
                    "WHEN d.studentAge BETWEEN 35 AND 39 THEN '35_39' " +
                    "ELSE '40_PLUS' " +
                "END = :ageRange)")
    List<StudentDemographics> findByFilters(
        @Param("applyCountryFilter") boolean applyCountryFilter,
        @Param("countryIds") List<Integer> countryIds,
        @Param("genderId") Integer genderId,
        @Param("ageRange") String ageRange,
        @Param("levelId") Integer levelId,
        @Param("studyTypeId") Integer studyTypeId,
        @Param("degreeId") Integer degreeId,
        @Param("majorId") Integer majorId,
        @Param("statusId") Integer statusId
    );
}

