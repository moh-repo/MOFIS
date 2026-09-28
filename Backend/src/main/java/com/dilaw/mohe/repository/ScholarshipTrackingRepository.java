package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.ScholarshipTracking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScholarshipTrackingRepository extends JpaRepository<ScholarshipTracking, Long> {

    /**
     * The row's student exists and is not deleted (live QA H7): V_Scholarship_Tracking
     * reads STUDENT_DATA without the IS_DELETED filter, and changing the view is a migration.
     */
    String LIVE_STUDENT = "EXISTS (SELECT live FROM StudentDatum live WHERE live.studentId = s.studentId "
            + "AND (live.isDeleted = false OR live.isDeleted IS NULL))";
    
    @Query("SELECT s FROM ScholarshipTracking s WHERE (s.scholarshipType = :type OR s.scholarshipTypeEn = :type) AND " + LIVE_STUDENT + " AND " +
           "(:applyCountryFilter = false OR EXISTS (SELECT sd FROM StudentDatum sd WHERE sd.studentId = s.studentId AND sd.studyCountryIn IN :countryIds))")
    List<ScholarshipTracking> findByScholarshipType(
            @Param("type") String type,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT s FROM ScholarshipTracking s WHERE (s.scholarshipProvider = :provider OR s.scholarshipProviderEn = :provider) AND " + LIVE_STUDENT + " AND " +
           "(:applyCountryFilter = false OR EXISTS (SELECT sd FROM StudentDatum sd WHERE sd.studentId = s.studentId AND sd.studyCountryIn IN :countryIds))")
    List<ScholarshipTracking> findByScholarshipProvider(
            @Param("provider") String provider,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT s FROM ScholarshipTracking s WHERE (s.scholarshipStatus = :status OR s.scholarshipStatusEn = :status) AND " + LIVE_STUDENT + " AND " +
           "(:applyCountryFilter = false OR EXISTS (SELECT sd FROM StudentDatum sd WHERE sd.studentId = s.studentId AND sd.studyCountryIn IN :countryIds))")
    List<ScholarshipTracking> findByScholarshipStatus(
            @Param("status") String status,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT s FROM ScholarshipTracking s WHERE " + LIVE_STUDENT + " AND " +
           "(:type IS NULL OR s.scholarshipType = :type OR s.scholarshipTypeEn = :type) AND " +
           "(:provider IS NULL OR s.scholarshipProvider = :provider OR s.scholarshipProviderEn = :provider) AND " +
           "(:status IS NULL OR s.scholarshipStatus = :status OR s.scholarshipStatusEn = :status) AND " +
           "(:applyCountryFilter = false OR EXISTS (SELECT sd FROM StudentDatum sd WHERE sd.studentId = s.studentId AND sd.studyCountryIn IN :countryIds)) AND " +
           "(:search IS NULL OR LOWER(s.studentName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.studentPassportNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ScholarshipTracking> findByFilters(
        @Param("type") String type,
        @Param("provider") String provider,
        @Param("status") String status,
        @Param("applyCountryFilter") boolean applyCountryFilter,
        @Param("countryIds") List<Integer> countryIds,
        @Param("search") String search,
        Pageable pageable
    );
    
    @Query("SELECT DISTINCT s.scholarshipType FROM ScholarshipTracking s WHERE s.scholarshipType IS NOT NULL")
    List<String> findDistinctTypes();
    
    @Query("SELECT DISTINCT s.scholarshipProvider FROM ScholarshipTracking s WHERE s.scholarshipProvider IS NOT NULL")
    List<String> findDistinctProviders();
    
    @Query("SELECT DISTINCT s.scholarshipStatus FROM ScholarshipTracking s WHERE s.scholarshipStatus IS NOT NULL")
    List<String> findDistinctStatuses();
    
}

