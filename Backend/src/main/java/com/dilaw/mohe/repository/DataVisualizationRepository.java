package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.DataVisualization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Every finder takes the caller's country scope (RPT-05). The view is partitioned by
 * study country, and the endpoints used to return every partition and leave the
 * narrowing to row-level security alone: one policy switched off, or one connection
 * without its session context, and a scoped user read every country's figures.
 */
@Repository
public interface DataVisualizationRepository extends JpaRepository<DataVisualization, Long> {

    String SCOPE = "(:applyCountryFilter = false OR d.studyCountryInId IN :countryIds)";

    @Query("SELECT d FROM DataVisualization d WHERE d.chartType = :chartType AND " + SCOPE + " ORDER BY d.sortOrder")
    List<DataVisualization> findByChartType(@Param("chartType") String chartType,
                                            @Param("applyCountryFilter") boolean applyCountryFilter,
                                            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT d FROM DataVisualization d WHERE d.chartName = :chartName AND " + SCOPE + " ORDER BY d.sortOrder")
    List<DataVisualization> findByChartName(@Param("chartName") String chartName,
                                            @Param("applyCountryFilter") boolean applyCountryFilter,
                                            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT d FROM DataVisualization d WHERE d.category = :category AND " + SCOPE + " ORDER BY d.sortOrder")
    List<DataVisualization> findByCategory(@Param("category") String category,
                                           @Param("applyCountryFilter") boolean applyCountryFilter,
                                           @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT DISTINCT d.chartName FROM DataVisualization d WHERE " + SCOPE + " ORDER BY d.chartName")
    List<String> findAllChartNames(@Param("applyCountryFilter") boolean applyCountryFilter,
                                   @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT d FROM DataVisualization d WHERE " + SCOPE + " ORDER BY d.sortOrder")
    List<DataVisualization> findAllOrderedBySortOrder(@Param("applyCountryFilter") boolean applyCountryFilter,
                                                      @Param("countryIds") List<Integer> countryIds);
}
