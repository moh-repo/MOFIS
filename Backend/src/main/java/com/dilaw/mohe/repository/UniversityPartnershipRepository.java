package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.UniversityPartnership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UniversityPartnershipRepository extends JpaRepository<UniversityPartnership, Long> {

    @Query("SELECT u FROM UniversityPartnership u WHERE u.countryName = :countryName AND " +
            "(:applyCountryFilter = false OR u.countryId IN :countryIds) ORDER BY u.totalStudents DESC")
    List<UniversityPartnership> findByCountry(
            @Param("countryName") String countryName,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT u FROM UniversityPartnership u WHERE u.activeStudents > :minStudents AND " +
            "(:applyCountryFilter = false OR u.countryId IN :countryIds) ORDER BY u.activeStudents DESC")
    List<UniversityPartnership> findByMinimumActiveStudents(
            @Param("minStudents") Integer minStudents,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT u FROM UniversityPartnership u WHERE " +
            "(:applyCountryFilter = false OR u.countryId IN :countryIds) ORDER BY u.totalStudents DESC")
    List<UniversityPartnership> findAllOrderedByStudentCount(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

}
