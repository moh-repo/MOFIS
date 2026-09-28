package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.BudgetAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetAnalysisRepository extends JpaRepository<BudgetAnalysis, Long> {

    @Query("SELECT b FROM BudgetAnalysis b WHERE " +
           "(:applyCountryFilter = false OR b.studyCountryInId IN :countryIds) AND " +
           "(:category IS NULL OR b.budgetCategory = :category) AND " +
           "(:year IS NULL OR b.budgetYear = :year) AND " +
           "(:quarter IS NULL OR b.budgetQuarter = :quarter)")
    List<BudgetAnalysis> findByFilters(
        @Param("applyCountryFilter") boolean applyCountryFilter,
        @Param("countryIds") List<Integer> countryIds,
        @Param("category") String category,
        @Param("year") Integer year,
        @Param("quarter") Integer quarter
    );

    @Query("SELECT DISTINCT b.budgetCategory FROM BudgetAnalysis b WHERE " +
           "(:applyCountryFilter = false OR b.studyCountryInId IN :countryIds) ORDER BY b.budgetCategory")
    List<String> findDistinctCategories(
        @Param("applyCountryFilter") boolean applyCountryFilter,
        @Param("countryIds") List<Integer> countryIds
    );

    @Query("SELECT DISTINCT b.budgetYear FROM BudgetAnalysis b WHERE " +
           "(:applyCountryFilter = false OR b.studyCountryInId IN :countryIds) ORDER BY b.budgetYear DESC")
    List<Integer> findDistinctYears(
        @Param("applyCountryFilter") boolean applyCountryFilter,
        @Param("countryIds") List<Integer> countryIds
    );

    @Query("SELECT DISTINCT b.budgetQuarter FROM BudgetAnalysis b WHERE " +
           "(:applyCountryFilter = false OR b.studyCountryInId IN :countryIds) " +
           "AND (:year IS NULL OR b.budgetYear = :year) ORDER BY b.budgetQuarter")
    List<Integer> findDistinctQuarters(
        @Param("applyCountryFilter") boolean applyCountryFilter,
        @Param("countryIds") List<Integer> countryIds,
        @Param("year") Integer year
    );
}
