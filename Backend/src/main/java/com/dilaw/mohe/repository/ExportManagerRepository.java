package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.ExportManager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExportManagerRepository extends JpaRepository<ExportManager, Long> {
    
    @Query("SELECT e FROM ExportManager e WHERE e.exportStatus = :status")
    List<ExportManager> findByExportStatus(@Param("status") String status);
    
    @Query("SELECT e FROM ExportManager e WHERE e.isAvailable = 1")
    List<ExportManager> findAvailableExports();
    
    @Query("SELECT e FROM ExportManager e WHERE e.defaultFormat = :format")
    List<ExportManager> findByDefaultFormat(@Param("format") String format);
    
    @Query("SELECT e FROM ExportManager e ORDER BY e.lastExportDate DESC")
    List<ExportManager> findAllOrderedByLastExportDate();
}

