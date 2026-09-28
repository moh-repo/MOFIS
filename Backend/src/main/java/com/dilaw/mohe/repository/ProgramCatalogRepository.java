package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.ProgramCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgramCatalogRepository extends JpaRepository<ProgramCatalog, ProgramCatalog.ProgramCatalogId> {

    @Query("SELECT p FROM ProgramCatalog p WHERE p.universityId = :universityId AND " +
            "(:applyCountryFilter = false OR p.countryId IN :countryIds) ORDER BY p.totalStudentsEnrolled DESC")
    List<ProgramCatalog> findByUniversityId(
            @Param("universityId") Long universityId,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT p FROM ProgramCatalog p WHERE p.degreeLevelId = :degreeLevelId AND " +
            "(:applyCountryFilter = false OR p.countryId IN :countryIds) ORDER BY p.totalStudentsEnrolled DESC")
    List<ProgramCatalog> findByDegreeLevel(
            @Param("degreeLevelId") Integer degreeLevelId,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT p FROM ProgramCatalog p WHERE " +
            "(:applyCountryFilter = false OR p.countryId IN :countryIds) ORDER BY p.totalStudentsEnrolled DESC")
    List<ProgramCatalog> findAllOrderedByEnrollment(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);
}
