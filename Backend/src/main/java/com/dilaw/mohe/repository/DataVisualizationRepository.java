package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.DataVisualization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DataVisualizationRepository extends JpaRepository<DataVisualization, Long> {
    
    @Query("SELECT d FROM DataVisualization d WHERE d.chartType = :chartType ORDER BY d.sortOrder")
    List<DataVisualization> findByChartType(@Param("chartType") String chartType);
    
    @Query("SELECT d FROM DataVisualization d WHERE d.chartName = :chartName ORDER BY d.sortOrder")
    List<DataVisualization> findByChartName(@Param("chartName") String chartName);
    
    @Query("SELECT d FROM DataVisualization d WHERE d.category = :category ORDER BY d.sortOrder")
    List<DataVisualization> findByCategory(@Param("category") String category);
    
    @Query("SELECT DISTINCT d.chartName FROM DataVisualization d ORDER BY d.chartName")
    List<String> findAllChartNames();
    
    @Query("SELECT d FROM DataVisualization d ORDER BY d.sortOrder")
    List<DataVisualization> findAllOrderedBySortOrder();
}

