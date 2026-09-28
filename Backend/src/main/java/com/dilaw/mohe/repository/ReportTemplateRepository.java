package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.ReportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long> {
    
    @Query("SELECT r FROM ReportTemplate r WHERE r.reportType = :reportType")
    List<ReportTemplate> findByReportType(@Param("reportType") String reportType);
    
    @Query("SELECT r FROM ReportTemplate r WHERE r.isPublic = 1")
    List<ReportTemplate> findPublicTemplates();
        
    @Query("SELECT r FROM ReportTemplate r ORDER BY r.usageCount DESC")
    List<ReportTemplate> findAllOrderedByUsageCount();
    
    @Query("SELECT r FROM ReportTemplate r ORDER BY r.lastUsedDate DESC")
    List<ReportTemplate> findAllOrderedByLastUsedDate();
}

