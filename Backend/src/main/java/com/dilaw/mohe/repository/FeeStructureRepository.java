package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.FeeStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeeStructureRepository extends JpaRepository<FeeStructure, FeeStructure.FeeStructureId> {

    @Query("SELECT f FROM FeeStructure f WHERE f.universityId = :universityId AND " +
            "(:applyCountryFilter = false OR f.countryId IN :countryIds) ORDER BY f.degreeLevelId")
    List<FeeStructure> findByUniversityId(
            @Param("universityId") Long universityId,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT f FROM FeeStructure f WHERE f.degreeLevelId = :degreeLevelId AND " +
            "(:applyCountryFilter = false OR f.countryId IN :countryIds) ORDER BY f.avgFeeAmount DESC")
    List<FeeStructure> findByDegreeLevel(
            @Param("degreeLevelId") Integer degreeLevelId,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT f FROM FeeStructure f WHERE " +
            "(:applyCountryFilter = false OR f.countryId IN :countryIds) ORDER BY f.avgFeeAmount DESC")
    List<FeeStructure> findAllOrderedByFeeAmount(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT f FROM FeeStructure f WHERE f.countryName = :countryName AND " +
            "(:applyCountryFilter = false OR f.countryId IN :countryIds) ORDER BY f.avgFeeAmount DESC")
    List<FeeStructure> findByCountry(
            @Param("countryName") String countryName,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);
}
