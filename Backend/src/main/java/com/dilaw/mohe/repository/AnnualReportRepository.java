package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.AnnualReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnualReportRepository extends JpaRepository<AnnualReport, AnnualReport.AnnualReportId> {

    @Query("SELECT a FROM AnnualReport a WHERE a.reportYear >= :startYear AND a.reportYear <= :endYear AND " +
            "(:applyCountryFilter = false OR a.countryId IN :countryIds) ORDER BY a.reportYear")
    List<AnnualReport> findByYearRange(
            @Param("startYear") Integer startYear,
            @Param("endYear") Integer endYear,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT a FROM AnnualReport a WHERE a.reportYear = :year AND " +
            "(:applyCountryFilter = false OR a.countryId IN :countryIds)")
    AnnualReport findByYear(
            @Param("year") Integer year,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT a FROM AnnualReport a WHERE " +
            "(:applyCountryFilter = false OR a.countryId IN :countryIds) ORDER BY a.reportYear DESC")
    List<AnnualReport> findAllOrdered(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);
}
