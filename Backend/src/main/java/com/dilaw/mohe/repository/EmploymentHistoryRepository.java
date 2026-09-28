package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.EmploymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmploymentHistoryRepository extends JpaRepository<EmploymentHistory, Integer> {

    List<EmploymentHistory> findByNodeId(Integer nodeId);

    List<EmploymentHistory> findByNodeIdAndIsCurrentTrue(Integer nodeId);

    @Query("SELECT DISTINCT eh.companyName FROM EmploymentHistory eh WHERE eh.companyName IS NOT NULL ORDER BY eh.companyName")
    List<String> findDistinctCompanies();

    @Query("SELECT DISTINCT eh.industry FROM EmploymentHistory eh WHERE eh.industry IS NOT NULL ORDER BY eh.industry")
    List<String> findDistinctIndustries();

    @Query("SELECT COUNT(eh) FROM EmploymentHistory eh WHERE eh.isCurrent = true")
    Long countCurrentEmployments();
}

