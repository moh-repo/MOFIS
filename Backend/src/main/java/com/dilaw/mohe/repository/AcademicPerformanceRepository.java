package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.AcademicPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcademicPerformanceRepository extends JpaRepository<AcademicPerformance, Integer> {
    
    
    List<AcademicPerformance> findByStudentCountryId(Integer countryId);
    
    List<AcademicPerformance> findByStudentMajorId(Integer majorId);
    
    @Query("SELECT a FROM AcademicPerformance a WHERE " +
           "(:applyCountryFilter = false OR a.studyCountryInId IN :countryIds) AND " +
           "(:majorId IS NULL OR a.studentMajorId = :majorId) AND " +
           "(:statusId IS NULL OR a.studentStudyStatusId = :statusId) AND " +
           "(:degreeId IS NULL OR a.studentDegreeId = :degreeId) AND " +
           "(:levelId IS NULL OR a.studentCurrentLevelId = :levelId)")
    List<AcademicPerformance> findByFilters(
        @Param("applyCountryFilter") boolean applyCountryFilter,
        @Param("countryIds") java.util.List<Integer> countryIds,
        @Param("majorId") Integer majorId,
        @Param("statusId") Integer statusId,
        @Param("degreeId") Integer degreeId,
        @Param("levelId") Integer levelId
    );
}

