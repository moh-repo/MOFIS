package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentFinancialDataFormV2;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Rows of the quarterly financial assistance statement.
 *
 * <p>Ordering is the printed order: students are grouped by sponsoring entity
 * (the template's group and colour band), then by first payment quarter, then
 * by name — which is how the ministry's own statement is laid out.
 */
@Repository
public interface StudentFinancialDataFormV2Repository extends JpaRepository<StudentFinancialDataFormV2, Integer> {

    String SCOPE = "(:applyCountryFilter = false OR s.studyCountryInId IN :countryIds) ";
    String ORDER = "ORDER BY s.entityName, s.studentFirstReceiptOfDue, s.studentName";

    @Query("SELECT s FROM StudentFinancialDataFormV2 s WHERE "
            + "s.studentFinReceivedYear = :year AND s.studentFinReceivedMonth = :quarter AND "
            + SCOPE + ORDER)
    List<StudentFinancialDataFormV2> findByYearAndQuarterScoped(
            @Param("year") Integer year,
            @Param("quarter") Integer quarter,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT s FROM StudentFinancialDataFormV2 s WHERE "
            + "s.studyCountryInName = :countryName AND "
            + "s.studentFinReceivedYear = :year AND s.studentFinReceivedMonth = :quarter AND "
            + SCOPE + ORDER)
    List<StudentFinancialDataFormV2> findByCountryYearAndQuarterScoped(
            @Param("countryName") String countryName,
            @Param("year") Integer year,
            @Param("quarter") Integer quarter,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT DISTINCT s.studentFinReceivedYear FROM StudentFinancialDataFormV2 s WHERE "
            + SCOPE + "AND s.studentFinReceivedYear IS NOT NULL ORDER BY s.studentFinReceivedYear DESC")
    List<Integer> findDistinctYearsScoped(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT DISTINCT s.studentFinReceivedMonth FROM StudentFinancialDataFormV2 s WHERE "
            + SCOPE + "AND s.studentFinReceivedMonth BETWEEN 1 AND 4 ORDER BY s.studentFinReceivedMonth")
    List<Integer> findDistinctQuartersScoped(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT DISTINCT s.studyCountryInName FROM StudentFinancialDataFormV2 s WHERE "
            + SCOPE + "AND s.studyCountryInName <> '' ORDER BY s.studyCountryInName")
    List<String> findDistinctCountriesScoped(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);
}
