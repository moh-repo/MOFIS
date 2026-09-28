package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentListDetailed;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentListDetailedRepository extends JpaRepository<StudentListDetailed, Integer> {
    
    @Query("SELECT s FROM StudentListDetailed s WHERE " +
           "(:applyCountryFilter = false OR s.studyCountryInId IN :countryIds) AND " +
           "(:majorId IS NULL OR s.studentMajorId = :majorId) AND " +
           "(:statusId IS NULL OR s.studentStudyStatusId = :statusId) AND " +
           "(:genderId IS NULL OR s.studentGender = (SELECT g.studentGenderName FROM StudentGender g WHERE g.id = :genderId)) AND " +
           "(:degreeId IS NULL OR s.studentDegreeId = :degreeId) AND " +
           "(:levelId IS NULL OR s.studentCurrentLevelId = :levelId) AND " +
           "(:studyTypeId IS NULL OR s.studentStudyTypeId = :studyTypeId) AND " +
           "(:universityTypeId IS NULL OR s.studentUniversityTypeId = :universityTypeId) AND " +
           "(:agencyTypeId IS NULL OR s.studentAgencyTypeId = :agencyTypeId) AND " +
           "(:ministryTypeId IS NULL OR s.studentMinistryTypeId = :ministryTypeId)")
    Page<StudentListDetailed> findByFilters(
        @Param("applyCountryFilter") boolean applyCountryFilter,
        @Param("countryIds") java.util.List<Integer> countryIds,
        @Param("majorId") Integer majorId,
        @Param("statusId") Integer statusId,
        @Param("genderId") Integer genderId,
        @Param("degreeId") Integer degreeId,
        @Param("levelId") Integer levelId,
        @Param("studyTypeId") Integer studyTypeId,
        @Param("universityTypeId") Integer universityTypeId,
        @Param("agencyTypeId") Integer agencyTypeId,
        @Param("ministryTypeId") Integer ministryTypeId,
        Pageable pageable
    );
    
    @Query("SELECT s FROM StudentListDetailed s WHERE " +
           "(:applyCountryFilter = false OR s.studyCountryInId IN :countryIds) AND " +
           "(LOWER(s.studentName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.studentPassportNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<StudentListDetailed> searchStudents(
        @Param("search") String search,
        @Param("applyCountryFilter") boolean applyCountryFilter,
        @Param("countryIds") java.util.List<Integer> countryIds,
        Pageable pageable);
}

