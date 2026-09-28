package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentStatusReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentStatusReportRepository extends JpaRepository<StudentStatusReport, StudentStatusReport.StudentStatusReportId> {
    
    @Query("SELECT s FROM StudentStatusReport s WHERE s.statusCategory = :category")
    List<StudentStatusReport> findByStatusCategory(@Param("category") String category);
    
    @Query("SELECT s FROM StudentStatusReport s WHERE s.studentCountryId = :countryId")
    List<StudentStatusReport> findByCountry(@Param("countryId") Integer countryId);
    
    /**
     * Status categories present in the caller's country scope.
     *
     * <p>This had no country predicate, so a user restricted to one country was
     * shown the ministry-wide category list and could pick a category that
     * returned nothing for them — a scope leak in the filter, and a confusing
     * empty report. The scope arguments now match {@link #search}.
     */
    @Query("SELECT DISTINCT s.statusCategory FROM StudentStatusReport s "
            + "WHERE (:applyCountryFilter = false OR s.studyCountryInId IN :countryIds) "
            + "AND s.statusCategory IS NOT NULL")
    List<String> findDistinctStatusCategories(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT s FROM StudentStatusReport s " +
            "WHERE (:statusId IS NULL OR s.studentStudyStatusId = :statusId) " +
            "AND (:applyCountryFilter = false OR s.studyCountryInId IN :countryIds) " +
            "AND (:majorId IS NULL OR s.studentMajorId = :majorId)")
    List<StudentStatusReport> search(
            @Param("statusId") Integer statusId,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds,
            @Param("majorId") Integer majorId);
}

