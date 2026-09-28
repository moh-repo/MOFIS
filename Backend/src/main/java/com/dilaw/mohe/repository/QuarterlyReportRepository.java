package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.QuarterlyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuarterlyReportRepository extends JpaRepository<QuarterlyReport, QuarterlyReport.QuarterlyReportId> {

    @Query("SELECT q FROM QuarterlyReport q WHERE " +
            "(:applyCountryFilter = false OR q.countryId IN :countryIds) " +
            "ORDER BY q.reportYear DESC, q.reportQuarter")
    List<QuarterlyReport> findAllFiltered(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT q FROM QuarterlyReport q WHERE q.reportYear = :year AND " +
            "(:applyCountryFilter = false OR q.countryId IN :countryIds) ORDER BY q.reportQuarter")
    List<QuarterlyReport> findByYear(
            @Param("year") Integer year,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT q FROM QuarterlyReport q WHERE q.reportYear >= :startYear AND q.reportYear <= :endYear AND " +
            "(:applyCountryFilter = false OR q.countryId IN :countryIds) ORDER BY q.reportYear, q.reportQuarter")
    List<QuarterlyReport> findByYearRange(
            @Param("startYear") Integer startYear,
            @Param("endYear") Integer endYear,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT q FROM QuarterlyReport q WHERE q.reportYear = :year AND q.reportQuarter = :quarter AND " +
            "(:applyCountryFilter = false OR q.countryId IN :countryIds)")
    QuarterlyReport findByYearAndQuarter(
            @Param("year") Integer year,
            @Param("quarter") Integer quarter,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT DISTINCT q.reportYear FROM QuarterlyReport q WHERE " +
            "(:applyCountryFilter = false OR q.countryId IN :countryIds) ORDER BY q.reportYear DESC")
    List<Integer> findAllYears(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);
}
