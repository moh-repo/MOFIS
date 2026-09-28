package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.BankTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
    
    @Query("SELECT t FROM BankTransaction t WHERE " +
           "(:type IS NULL OR t.transactionType = :type OR t.transactionTypeEn = :type) AND " +
           "(:bankName IS NULL OR t.bankName = :bankName OR t.bankNameEn = :bankName) AND " +
           "(:branchName IS NULL OR t.bankBranch = :branchName OR t.bankBranchEn = :branchName) AND " +
           "(:currency IS NULL OR t.transactionCurrency = :currency OR t.transactionCurrencyEn = :currency) AND " +
           "(:startDate IS NULL OR t.transactionDate >= :startDate) AND " +
           "(:endDate IS NULL OR t.transactionDate <= :endDate) AND " +
           "(:applyCountryFilter = false OR EXISTS (SELECT sd FROM StudentDatum sd WHERE sd.studentId = t.studentId AND sd.studyCountryIn IN :countryIds)) AND " +
           "(:search IS NULL OR LOWER(t.beneficiaryName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.referenceNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<BankTransaction> findByFilters(
        @Param("type") String type,
        @Param("bankName") String bankName,
        @Param("branchName") String branchName,
        @Param("currency") String currency,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("applyCountryFilter") boolean applyCountryFilter,
        @Param("countryIds") List<Integer> countryIds,
        @Param("search") String search,
        Pageable pageable
    );

    @Query("SELECT t FROM BankTransaction t WHERE (t.transactionType = :type OR t.transactionTypeEn = :type) AND " +
           "(:applyCountryFilter = false OR EXISTS (SELECT sd FROM StudentDatum sd WHERE sd.studentId = t.studentId AND sd.studyCountryIn IN :countryIds))")
    List<BankTransaction> findByTransactionType(
            @Param("type") String type,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT t FROM BankTransaction t WHERE (t.bankName = :bankName OR t.bankNameEn = :bankName) AND " +
           "(:applyCountryFilter = false OR EXISTS (SELECT sd FROM StudentDatum sd WHERE sd.studentId = t.studentId AND sd.studyCountryIn IN :countryIds))")
    List<BankTransaction> findByBankName(
            @Param("bankName") String bankName,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);
    
    @Query("SELECT DISTINCT t.transactionType FROM BankTransaction t")
    List<String> findDistinctTypes();
    
    @Query("SELECT DISTINCT t.bankName FROM BankTransaction t")
    List<String> findDistinctBanks();

    @Query("SELECT DISTINCT t.bankBranch FROM BankTransaction t WHERE t.bankBranch IS NOT NULL AND t.bankBranch <> ''")
    List<String> findDistinctBranches();

    @Query("SELECT DISTINCT t.transactionCurrency FROM BankTransaction t WHERE t.transactionCurrency IS NOT NULL AND t.transactionCurrency <> ''")
    List<String> findDistinctCurrencies();
}

