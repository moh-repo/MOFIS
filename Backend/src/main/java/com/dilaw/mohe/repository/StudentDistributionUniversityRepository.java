package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentDistributionUniversity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentDistributionUniversityRepository extends JpaRepository<StudentDistributionUniversity, Long> {

    @Query("SELECT s FROM StudentDistributionUniversity s WHERE " +
            "(:applyCountryFilter = false OR s.countryId IN :countryIds) ORDER BY s.totalStudents DESC")
    List<StudentDistributionUniversity> findAllOrderedByStudentCount(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT s FROM StudentDistributionUniversity s WHERE s.countryName = :countryName AND " +
            "(:applyCountryFilter = false OR s.countryId IN :countryIds) ORDER BY s.totalStudents DESC")
    List<StudentDistributionUniversity> findByCountry(
            @Param("countryName") String countryName,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT s FROM StudentDistributionUniversity s WHERE s.activeStudents > :minStudents AND " +
            "(:applyCountryFilter = false OR s.countryId IN :countryIds) ORDER BY s.activeStudents DESC")
    List<StudentDistributionUniversity> findByMinimumActiveStudents(
            @Param("minStudents") Integer minStudents,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT s FROM StudentDistributionUniversity s WHERE s.totalStudents >= :threshold AND " +
            "(:applyCountryFilter = false OR s.countryId IN :countryIds) ORDER BY s.totalStudents DESC")
    List<StudentDistributionUniversity> findLargeUniversities(
            @Param("threshold") Integer threshold,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);
}
