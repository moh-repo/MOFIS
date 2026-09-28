package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.MonthlyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, MonthlyReport.MonthlyReportId> {

    @Query("SELECT m FROM MonthlyReport m WHERE " +
            "(:applyCountryFilter = false OR m.countryId IN :countryIds) " +
            "ORDER BY m.reportYear DESC, m.reportMonth")
    List<MonthlyReport> findAllFiltered(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT m FROM MonthlyReport m WHERE m.reportYear = :year AND " +
            "(:applyCountryFilter = false OR m.countryId IN :countryIds) ORDER BY m.reportMonth")
    List<MonthlyReport> findByYear(
            @Param("year") Integer year,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT m FROM MonthlyReport m WHERE m.reportYear >= :startYear AND m.reportYear <= :endYear AND " +
            "(:applyCountryFilter = false OR m.countryId IN :countryIds) ORDER BY m.reportYear, m.reportMonth")
    List<MonthlyReport> findByYearRange(
            @Param("startYear") Integer startYear,
            @Param("endYear") Integer endYear,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT m FROM MonthlyReport m WHERE m.reportYear = :year AND m.reportMonth = :month AND " +
            "(:applyCountryFilter = false OR m.countryId IN :countryIds)")
    MonthlyReport findByYearAndMonth(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT DISTINCT m.reportYear FROM MonthlyReport m WHERE " +
            "(:applyCountryFilter = false OR m.countryId IN :countryIds) ORDER BY m.reportYear DESC")
    List<Integer> findAllYears(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);
}
