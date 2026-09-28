package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentExtension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentExtensionRepository extends JpaRepository<StudentExtension, Integer>,
        JpaSpecificationExecutor<StudentExtension> {

    @Query("""
            SELECT se FROM StudentExtension se
            WHERE (:studentId IS NULL OR se.studentId = :studentId)
              AND (:filterStudentId IS NULL OR se.studentId = :filterStudentId)
              AND (:extensionType IS NULL OR LOWER(TRIM(se.extensionType)) = :extensionType)
              AND (:status IS NULL OR LOWER(TRIM(se.extensionStatus)) = :status)
              AND (:passport IS NULL OR se.studentId IN (
                  SELECT sd.studentId FROM StudentDatum sd
                  WHERE LOWER(sd.studentPassportNumber) LIKE LOWER(CONCAT('%', :passport, '%'))))
            ORDER BY se.extensionStartDate DESC, se.createdDate DESC, se.extensionId DESC
            """)
    List<StudentExtension> findByFilters(@Param("studentId") Integer studentId,
                                         @Param("filterStudentId") Integer filterStudentId,
                                         @Param("extensionType") String extensionType,
                                         @Param("status") String status,
                                         @Param("passport") String passport);

    List<StudentExtension> findByStudentIdOrderByExtensionIdAsc(Integer studentId);

    @Query("SELECT DISTINCT se.extensionType FROM StudentExtension se WHERE se.extensionType IS NOT NULL ORDER BY se.extensionType")
    List<String> findDistinctExtensionTypes();

    @Query("SELECT DISTINCT se.extensionStatus FROM StudentExtension se WHERE se.extensionStatus IS NOT NULL ORDER BY se.extensionStatus")
    List<String> findDistinctStatuses();

    @Query("SELECT DISTINCT se.studentId FROM StudentExtension se WHERE se.studentId IS NOT NULL")
    List<Integer> findDistinctStudentIds();

    long countByStudentIdAndExtensionType(Integer studentId, String extensionType);

    @Query("""
            SELECT se FROM StudentExtension se
            WHERE (:applyCountryFilter = false OR se.studentId IN (
                SELECT sd.studentId FROM StudentDatum sd WHERE sd.studyCountryIn IN :countryIds))
            """)
    List<StudentExtension> findAllScoped(@Param("applyCountryFilter") boolean applyCountryFilter,
                                         @Param("countryIds") List<Integer> countryIds);
}


