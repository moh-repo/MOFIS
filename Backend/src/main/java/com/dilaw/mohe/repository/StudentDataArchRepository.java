package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentDataArch;
import com.dilaw.mohe.service.StudentDataArchService.CountryFilterArgs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentDataArchRepository extends JpaRepository<StudentDataArch, Integer>, JpaSpecificationExecutor<StudentDataArch> {

    /**
     * The snapshot of one student for one period, if it is already there.
     *
     * <p>The archive's real key — see {@code StudentDataArchiveService}. The table has
     * no unique index behind it, so {@code findFirst} rather than a scalar: the two
     * historical loads left one row per student per period, but nothing enforces it.
     */
    Optional<StudentDataArch> findFirstByStudentIdAndCurrentQuarterOrderByIdAsc(Integer studentId, String currentQuarter);

    /** The same lookup for a page of students at once (quarter create, review Q4). */
    List<StudentDataArch> findByStudentIdInAndCurrentQuarterIn(java.util.Collection<Integer> studentIds,
                                                              java.util.Collection<String> currentQuarters);

    // Get distinct students with their latest data (highest STUDENT_ID for each student)
    @Query("SELECT s FROM StudentDataArch s WHERE s.id IN " +
           "(SELECT MAX(s2.id) FROM StudentDataArch s2 GROUP BY s2.studentName) AND " +
           "(:#{#filter.applyCountryFilter} = false OR s.studyCountryIn IN :#{#filter.countryIds})")
    Page<StudentDataArch> findDistinctStudentsWithLatestData(Pageable pageable, @Param("filter") CountryFilterArgs filter);
    
    // Get distinct students by name search with their latest data
    @Query("SELECT s FROM StudentDataArch s WHERE s.studentName LIKE CONCAT('%', :name, '%') AND s.id IN " +
           "(SELECT MAX(s2.id) FROM StudentDataArch s2 WHERE s2.studentName LIKE CONCAT('%', :name, '%') GROUP BY s2.studentName) AND " +
           "(:#{#filter.applyCountryFilter} = false OR s.studyCountryIn IN :#{#filter.countryIds})")
    List<StudentDataArch> findDistinctStudentsByNameWithLatestData(@Param("name") String name, @Param("filter") CountryFilterArgs filter);
    
    // Get all distinct students with their latest data (for basic endpoint)
    @Query("SELECT s FROM StudentDataArch s WHERE s.id IN " +
           "(SELECT MAX(s2.id) FROM StudentDataArch s2 GROUP BY s2.studentName) AND " +
           "(:#{#filter.applyCountryFilter} = false OR s.studyCountryIn IN :#{#filter.countryIds})")
    List<StudentDataArch> findAllDistinctStudentsWithLatestData(@Param("filter") CountryFilterArgs filter);
    
    // Get all financial records for a specific student (by student name)
    @Query("SELECT s FROM StudentDataArch s WHERE s.studentName = :studentName AND " +
           "(:#{#filter.applyCountryFilter} = false OR s.studyCountryIn IN :#{#filter.countryIds}) " +
           "ORDER BY s.studentAddedQuarter, s.archiveDate")
    List<StudentDataArch> findAllFinancialRecordsByStudentName(@Param("studentName") String studentName, @Param("filter") CountryFilterArgs filter);

    // Get all archived snapshots for a specific student by the real STUDENT_ID (repeatable
    // across rows). Preferred over the name-based lookup, which merges same-named students.
    @Query("SELECT s FROM StudentDataArch s WHERE s.studentId = :studentId AND " +
           "(:#{#filter.applyCountryFilter} = false OR s.studyCountryIn IN :#{#filter.countryIds}) " +
           "ORDER BY s.currentQuarter, s.studentAddedQuarter, s.archiveDate")
    List<StudentDataArch> findAllFinancialRecordsByStudentId(@Param("studentId") Integer studentId, @Param("filter") CountryFilterArgs filter);

    // Distinct students with field-level filters: ID (exact), name (contains), passport (contains)
    @Query("SELECT s FROM StudentDataArch s WHERE s.id IN " +
           "(SELECT MAX(s2.id) FROM StudentDataArch s2 GROUP BY s2.studentName) AND " +
           "(:studentId IS NULL OR s.studentId = :studentId) AND " +
           "(:name IS NULL OR LOWER(s.studentName) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
           "(:passport IS NULL OR LOWER(s.studentPassportNumber) LIKE LOWER(CONCAT('%', :passport, '%'))) AND " +
           "(:#{#filter.applyCountryFilter} = false OR s.studyCountryIn IN :#{#filter.countryIds})")
    Page<StudentDataArch> findDistinctStudentsWithFieldFilters(
           @Param("studentId") Integer studentId,
           @Param("name") String name,
           @Param("passport") String passport,
           Pageable pageable,
           @Param("filter") CountryFilterArgs filter);
}
