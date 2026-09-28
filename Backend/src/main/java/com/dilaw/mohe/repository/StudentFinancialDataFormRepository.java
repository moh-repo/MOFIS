package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentFinancialDataForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentFinancialDataFormRepository extends JpaRepository<StudentFinancialDataForm, Integer> {

    @Query("SELECT s FROM StudentFinancialDataForm s WHERE " +
           "(:applyCountryFilter = false OR s.studyCountryInName IN :countryNames) " +
           "ORDER BY s.studentId")
    List<StudentFinancialDataForm> findAllScoped(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryNames") List<String> countryNames);

    @Query("SELECT s FROM StudentFinancialDataForm s WHERE " +
           "s.studentFinReceivedYear = :year AND s.studentFinReceivedMonth = :quarter AND " +
           "(:applyCountryFilter = false OR s.studyCountryInName IN :countryNames) " +
           "ORDER BY s.studentId")
    List<StudentFinancialDataForm> findByYearAndQuarterScoped(
            @Param("year") Integer year,
            @Param("quarter") Integer quarter,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryNames") List<String> countryNames);

    @Query("SELECT s FROM StudentFinancialDataForm s WHERE " +
           "s.studyCountryInName = :countryName AND " +
           "s.studentFinReceivedYear = :year AND s.studentFinReceivedMonth = :quarter AND " +
           "(:applyCountryFilter = false OR s.studyCountryInName IN :allowedNames) " +
           "ORDER BY s.studentId")
    List<StudentFinancialDataForm> findByCountryYearAndQuarterScoped(
            @Param("countryName") String countryName,
            @Param("year") Integer year,
            @Param("quarter") Integer quarter,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("allowedNames") List<String> allowedNames);

    @Query("SELECT DISTINCT s.studentFinReceivedYear FROM StudentFinancialDataForm s WHERE " +
           "(:applyCountryFilter = false OR s.studyCountryInName IN :countryNames) " +
           "AND s.studentFinReceivedYear IS NOT NULL ORDER BY s.studentFinReceivedYear DESC")
    List<Integer> findDistinctYearsScoped(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryNames") List<String> countryNames);

    @Query("SELECT DISTINCT s.studyCountryInName FROM StudentFinancialDataForm s WHERE " +
           "(:applyCountryFilter = false OR s.studyCountryInName IN :countryNames) " +
           "AND s.studyCountryInName IS NOT NULL ORDER BY s.studyCountryInName")
    List<String> findDistinctCountriesScoped(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryNames") List<String> countryNames);

    @Query("SELECT DISTINCT s.studentFinReceivedMonth FROM StudentFinancialDataForm s WHERE " +
           "(:applyCountryFilter = false OR s.studyCountryInName IN :countryNames) " +
           "AND s.studentFinReceivedMonth IS NOT NULL AND s.studentFinReceivedMonth BETWEEN 1 AND 4 " +
           "ORDER BY s.studentFinReceivedMonth")
    List<Integer> findDistinctQuartersScoped(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryNames") List<String> countryNames);
}
