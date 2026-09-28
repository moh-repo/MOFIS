package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.MajorDistribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MajorDistributionRepository extends JpaRepository<MajorDistribution, MajorDistribution.MajorDistributionId> {
    
    @Query("SELECT m FROM MajorDistribution m WHERE m.studentCountryId = :countryId")
    List<MajorDistribution> findByCountry(@Param("countryId") Integer countryId);
    
    @Query("SELECT m FROM MajorDistribution m WHERE m.studentMajorId = :majorId")
    List<MajorDistribution> findByMajor(@Param("majorId") Integer majorId);
    
    @Query("SELECT m FROM MajorDistribution m ORDER BY m.studentCount DESC")
    List<MajorDistribution> findAllOrderByCount();

    @Query("SELECT m FROM MajorDistribution m " +
            "WHERE (:majorId IS NULL OR m.studentMajorId = :majorId) " +
            "AND (:degreeId IS NULL OR m.studentDegreeId = :degreeId) " +
            "AND (:applyCountryFilter = false OR m.studyCountryInId IN :countryIds) " +
            "AND (:studyTypeId IS NULL OR m.studentStudyTypeId = :studyTypeId)")
    List<MajorDistribution> search(
            @Param("majorId") Integer majorId,
            @Param("degreeId") Integer degreeId,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds,
            @Param("studyTypeId") Integer studyTypeId);
}

