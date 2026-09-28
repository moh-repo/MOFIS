package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.FinancialAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FinancialAuditRepository extends JpaRepository<FinancialAudit, Long> {
    
    @Query("SELECT a FROM FinancialAudit a WHERE " +
           "(:status IS NULL OR a.auditStatus = :status OR a.auditStatusEn = :status) AND " +
           "(:category IS NULL OR a.auditCategory = :category OR a.auditCategoryEn = :category) AND " +
           "(:year IS NULL OR a.fiscalYear = :year) AND " +
           "(:quarter IS NULL OR a.fiscalQuarter = :quarter) AND " +
           "(:bank IS NULL OR a.bankName = :bank OR a.bankNameEn = :bank) AND " +
           "(:auditor IS NULL OR a.auditorName = :auditor OR a.auditorNameEn = :auditor) AND " +
           "(:currency IS NULL OR a.auditCurrency = :currency OR a.auditCurrencyEn = :currency) AND " +
           "(:startDate IS NULL OR a.auditDate >= :startDate) AND " +
           "(:endDate IS NULL OR a.auditDate <= :endDate) AND " +
           "(:applyCountryFilter = false OR EXISTS (SELECT sd FROM StudentDatum sd WHERE sd.studentId = a.studentId AND sd.studyCountryIn IN :countryIds)) AND " +
           "(:search IS NULL OR LOWER(a.studentName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(COALESCE(STR(a.auditId), '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(COALESCE(a.auditNotes, '')) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<FinancialAudit> findByFilters(
        @Param("status") String status,
        @Param("category") String category,
        @Param("year") Integer year,
        @Param("quarter") Integer quarter,
        @Param("bank") String bank,
        @Param("auditor") String auditor,
        @Param("currency") String currency,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("applyCountryFilter") boolean applyCountryFilter,
        @Param("countryIds") List<Integer> countryIds,
        @Param("search") String search,
        Pageable pageable
    );

    @Query("SELECT a FROM FinancialAudit a WHERE (a.auditStatus = :status OR a.auditStatusEn = :status) AND " +
           "(:applyCountryFilter = false OR EXISTS (SELECT sd FROM StudentDatum sd WHERE sd.studentId = a.studentId AND sd.studyCountryIn IN :countryIds))")
    List<FinancialAudit> findByAuditStatus(
            @Param("status") String status,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT a FROM FinancialAudit a WHERE (a.auditCategory = :category OR a.auditCategoryEn = :category) AND " +
           "(:applyCountryFilter = false OR EXISTS (SELECT sd FROM StudentDatum sd WHERE sd.studentId = a.studentId AND sd.studyCountryIn IN :countryIds))")
    List<FinancialAudit> findByAuditCategory(
            @Param("category") String category,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT a FROM FinancialAudit a WHERE a.fiscalYear = :year AND " +
           "(:applyCountryFilter = false OR EXISTS (SELECT sd FROM StudentDatum sd WHERE sd.studentId = a.studentId AND sd.studyCountryIn IN :countryIds))")
    List<FinancialAudit> findByFiscalYear(
            @Param("year") Integer year,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);
    
    @Query("SELECT DISTINCT a.auditStatus FROM FinancialAudit a")
    List<String> findDistinctStatuses();
    
    @Query("SELECT DISTINCT a.auditCategory FROM FinancialAudit a")
    List<String> findDistinctCategories();
    
    @Query("SELECT DISTINCT a.fiscalYear FROM FinancialAudit a ORDER BY a.fiscalYear DESC")
    List<Integer> findDistinctYears();

    @Query("SELECT DISTINCT a.fiscalQuarter FROM FinancialAudit a WHERE a.fiscalQuarter IS NOT NULL ORDER BY a.fiscalQuarter")
    List<Integer> findDistinctQuarters();

    @Query("SELECT DISTINCT a.bankName FROM FinancialAudit a WHERE a.bankName IS NOT NULL AND a.bankName <> ''")
    List<String> findDistinctBanks();

    @Query("SELECT DISTINCT a.auditorName FROM FinancialAudit a WHERE a.auditorName IS NOT NULL AND a.auditorName <> ''")
    List<String> findDistinctAuditors();

    @Query("SELECT DISTINCT a.auditCurrency FROM FinancialAudit a WHERE a.auditCurrency IS NOT NULL AND a.auditCurrency <> ''")
    List<String> findDistinctCurrencies();
}

