package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.UniversityPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface UniversityPerformanceRepository extends JpaRepository<UniversityPerformance, Long> {

    @Query("SELECT u FROM UniversityPerformance u WHERE u.retentionRate >= :minRate AND " +
            "(:applyCountryFilter = false OR u.countryId IN :countryIds) ORDER BY u.retentionRate DESC")
    List<UniversityPerformance> findByMinimumRetentionRate(
            @Param("minRate") BigDecimal minRate,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT u FROM UniversityPerformance u WHERE " +
            "(:applyCountryFilter = false OR u.countryId IN :countryIds) ORDER BY u.totalEnrolled DESC")
    List<UniversityPerformance> findAllOrderedByEnrollment(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT u FROM UniversityPerformance u WHERE u.countryName = :countryName AND " +
            "(:applyCountryFilter = false OR u.countryId IN :countryIds) ORDER BY u.totalEnrolled DESC")
    List<UniversityPerformance> findByCountry(
            @Param("countryName") String countryName,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);
}
