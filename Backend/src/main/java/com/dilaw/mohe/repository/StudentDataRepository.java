package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.dto.LookupOptionDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentDataRepository extends JpaRepository<StudentDatum, Integer>,
        JpaSpecificationExecutor<StudentDatum> {

    /** Country-scoped paginated list with StudentCountry eager-loaded to prevent N+1. */
    @EntityGraph(attributePaths = {"StudentCountry"})
    @Override
    Page<StudentDatum> findAll(Specification<StudentDatum> spec, Pageable pageable);

       // List<StudentDatum> findByStudentId(int studentId);
       // StudentDatum findById(int id);
       // void deleteById(int id);

       boolean existsByStudentPassportNumberAndStudentIdNot(String passportNumber, Integer excludeId);
       boolean existsByStudentPassportNumber(String passportNumber);

       /** Distinct study-country IDs that have at least one non-deleted student record. */
       @Query("SELECT DISTINCT sd.studyCountryIn FROM StudentDatum sd WHERE sd.studyCountryIn IS NOT NULL AND sd.studyCountryIn <> 0")
       List<Integer> findDistinctStudyCountryInIds();

       // ── Alert-event detection ──────────────────────────────────────────────
       //
       // One query per scenario, replacing what AlertEventDetectionService used to do:
       // studentRepo.findAll() followed by a stream of filters, six times over — six
       // full reads of the student table on every nightly run to select, in the
       // largest case, a handful of rows.
       //
       // Every one of them repeats `isDeleted <> TRUE` rather than `= FALSE`, because
       // the column is nullable and a null there means the same thing as false: the
       // Java code they replace tested `!Boolean.TRUE.equals(...)`, which is true for
       // null, and `isDeleted = false` in SQL is not.

       /** Passports that expire inside the scenario's warning window — PASSPORT_EXPIRY_SOON. */
       @Query("SELECT sd FROM StudentDatum sd WHERE (sd.isDeleted IS NULL OR sd.isDeleted = FALSE) "
               + "AND sd.studentPassportExpireDate > :from AND sd.studentPassportExpireDate < :until")
       List<StudentDatum> findWithPassportExpiringBetween(@Param("from") Instant from,
                                                          @Param("until") Instant until);

       /** Passports that have already expired — PASSPORT_EXPIRED. */
       @Query("SELECT sd FROM StudentDatum sd WHERE (sd.isDeleted IS NULL OR sd.isDeleted = FALSE) "
               + "AND sd.studentPassportExpireDate < :asOf")
       List<StudentDatum> findWithPassportExpiredBefore(@Param("asOf") Instant asOf);

       /** Current level has reached the programme's length — LAST_STUDY_YEAR. */
       @Query("SELECT sd FROM StudentDatum sd WHERE (sd.isDeleted IS NULL OR sd.isDeleted = FALSE) "
               + "AND sd.studentStudyNoOfYears > 0 AND sd.studentCurrentLevelId = sd.studentStudyNoOfYears")
       List<StudentDatum> findAtFinalStudyYear();

       /** Current level has passed the programme's length — STUDY_DURATION_EXCEEDED. */
       @Query("SELECT sd FROM StudentDatum sd WHERE (sd.isDeleted IS NULL OR sd.isDeleted = FALSE) "
               + "AND sd.studentStudyNoOfYears > 0 AND sd.studentCurrentLevelId > sd.studentStudyNoOfYears")
       List<StudentDatum> findExceedingStudyDuration();

       /** A final quarter is registered — LAST_QUARTER. */
       @Query("SELECT sd FROM StudentDatum sd WHERE (sd.isDeleted IS NULL OR sd.isDeleted = FALSE) "
               + "AND sd.studentLastQuarter > 0")
       List<StudentDatum> findWithFinalQuarterRegistered();

       /**
        * No finance record at all — NO_FINANCE_RECORD.
        *
        * <p>The correlation is on {@code StudentFinance.id}, which looks wrong and is not:
        * that entity's {@code @Id} is mapped to the {@code STUDENT_ID} column
        * (StudentFinance.java), so the finance row's identifier <em>is</em> the student's.
        * The Java this replaces built a list of those ids and called {@code contains} on
        * it once per student, which is the same answer at O(students x finance rows).
        */
       @Query("SELECT sd FROM StudentDatum sd WHERE (sd.isDeleted IS NULL OR sd.isDeleted = FALSE) "
               + "AND NOT EXISTS (SELECT 1 FROM StudentFinance sf WHERE sf.id = sd.studentId)")
       List<StudentDatum> findWithoutFinanceRecord();

       // Eager-load StudentCountry to prevent N+1 on list pages
       @EntityGraph(attributePaths = {"StudentCountry"})
       @Override
       Page<StudentDatum> findAll(Pageable pageable);

       // Method to get recent students ordered by studentMidate (creation date)
       // descending
       List<StudentDatum> findTop5ByOrderByStudentMidateDesc();

       // Alternative method to get recent students ordered by studentId descending
       // (fallback)
       List<StudentDatum> findTop5ByOrderByStudentIdDesc();

       // Eager-load StudentCountry on filtered status queries too
       @EntityGraph(attributePaths = {"StudentCountry"})
       Page<StudentDatum> findByStudentStudyStatusId(Integer studentStudyStatusId, Pageable pageable);

       // Search students by name, passport number, or major name
       @EntityGraph(attributePaths = {"StudentCountry"})
       @Query("SELECT s FROM StudentDatum s WHERE " +
                     "LOWER(s.studentName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                     "LOWER(s.studentPassportNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                     "LOWER(s.studentMajorName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                     "CAST(s.studentId AS string) LIKE CONCAT('%', :searchTerm, '%')")
       Page<StudentDatum> searchStudents(@Param("searchTerm") String searchTerm, Pageable pageable);

       @Query("SELECT new com.dilaw.mohe.dto.LookupOptionDto(s.studentId, s.studentName) FROM StudentDatum s WHERE s.studentName IS NOT NULL ORDER BY s.studentName")
       List<LookupOptionDto> findAllStudentOptions();

       @Query("SELECT new com.dilaw.mohe.dto.LookupOptionDto(s.studentId, s.studentName) FROM StudentDatum s WHERE s.studentId IN :ids")
       List<LookupOptionDto> findStudentOptionsByIds(@Param("ids") Collection<Integer> ids);

       /**
        * Name and study country only, for reports that flatten other tables' rows
        * and need to label each with its student. The entitlements report used to
        * load the whole STUDENT_DATA table as entities for this — every column of
        * every student, to read two of them for a few hundred.
        */
       interface NameAndCountry {
              Integer getStudentId();
              String getStudentName();
              Integer getStudyCountryIn();
       }

       List<NameAndCountry> findByStudentIdIn(Collection<Integer> ids);

       // Find students by exact name match (case-insensitive)
       // Note: SQL Server default collation is case-insensitive, so exact match should
       // work
       // Trimming should be handled in the service layer
       List<StudentDatum> findByStudentNameIgnoreCase(String studentName);

       // Find students where name contains any of the provided terms (for flexible
       // matching)
       @Query("SELECT DISTINCT s FROM StudentDatum s WHERE s.studentName IS NOT NULL " +
                     "AND ((:term1 IS NOT NULL AND :term1 != '' AND LOWER(s.studentName) LIKE LOWER(CONCAT('%', :term1, '%'))) "
                     +
                     "OR (:term2 IS NOT NULL AND :term2 != '' AND LOWER(s.studentName) LIKE LOWER(CONCAT('%', :term2, '%'))) "
                     +
                     "OR (:term3 IS NOT NULL AND :term3 != '' AND LOWER(s.studentName) LIKE LOWER(CONCAT('%', :term3, '%'))) "
                     +
                     "OR (:term4 IS NOT NULL AND :term4 != '' AND LOWER(s.studentName) LIKE LOWER(CONCAT('%', :term4, '%'))) "
                     +
                     "OR (:term5 IS NOT NULL AND :term5 != '' AND LOWER(s.studentName) LIKE LOWER(CONCAT('%', :term5, '%'))))")
       List<StudentDatum> findByStudentNameContainingAnyTerms(@Param("term1") String term1,
                     @Param("term2") String term2,
                     @Param("term3") String term3,
                     @Param("term4") String term4,
                     @Param("term5") String term5);

       // Find students with names similar to the search term (for fuzzy matching
       // candidates)
       @Query("SELECT s FROM StudentDatum s WHERE s.studentName IS NOT NULL " +
                     "AND LOWER(s.studentName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
       List<StudentDatum> findCandidatesForFuzzyMatching(@Param("searchTerm") String searchTerm);

       // Find students whose name starts with the given first name (for first name
       // matching)
       @Query("SELECT s FROM StudentDatum s WHERE s.studentName IS NOT NULL " +
                     "AND (LOWER(s.studentName) LIKE LOWER(CONCAT(:firstName, '%')) " +
                     "OR LOWER(s.studentName) LIKE LOWER(CONCAT(:firstName, ' %')))")
       List<StudentDatum> findByFirstNameStartsWith(@Param("firstName") String firstName);

       // findByStudentStudyStatusId is declared above with @EntityGraph

       /*
        * The three "active / inactive" queries that used to sit here tested the
        * literal status id 1 and had no callers; the list filters through
        * StudentDataSpec.statusFilter with the ACTIVE id resolved by CODE.
        */

       long countByStudentStudyStatusId(Integer studentStudyStatusId);

       // Count students created after a certain date (New Students)
       long countByStudentMidateAfter(Instant date);

       /*
        * Monthly intake for the dashboard. The unscoped twin that used to sit above
        * this query had no IS_DELETED filter, so the ministry's chart counted
        * soft-deleted students while a country user's did not (removed 29 Aug 2026,
        * V070). Ministry callers now pass applyCountry = 0.
        */
       /* Rows are [year, month, count]: the month as data, named by the client in its language. */
       @Query(value = "SELECT YEAR(sd.student_midate) AS yr, MONTH(sd.student_midate) AS mo, COUNT(*) AS cnt " +
                     "FROM MOHECORE.STUDENT_DATA sd " +
                     "WHERE sd.student_midate >= :startDate " +
                     "AND ISNULL(sd.IS_DELETED, 0) = 0 " +
                     "AND (:applyCountry = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds)) " +
                     "GROUP BY YEAR(sd.student_midate), MONTH(sd.student_midate) " +
                     "ORDER BY YEAR(sd.student_midate), MONTH(sd.student_midate)", nativeQuery = true)
       List<Object[]> findStudentCountsByMonthSinceScoped(
                     @Param("startDate") Instant startDate,
                     @Param("applyCountry") int applyCountry,
                     @Param("countryIds") List<Integer> countryIds);

       /**
        * For info requests: student ids with an optional <b>study</b> country and study status
        * filter. The country is where the student studies (the attaché's country), not the
        * nationality it matched until 26 Sep 2026 (FIX-PLAN P5-4).
        */
       @Query("SELECT s.studentId FROM StudentDatum s WHERE (:countryId IS NULL OR s.studyCountryIn = :countryId) AND (:statusId IS NULL OR s.studentStudyStatusId = :statusId)")
       List<Integer> findStudentIdsByOptionalFilters(@Param("countryId") Integer countryId, @Param("statusId") Integer statusId);

       /** The same, limited to students of the given study countries (a country-scoped caller; live QA H2). */
       @Query("SELECT s.studentId FROM StudentDatum s WHERE (:countryId IS NULL OR s.studyCountryIn = :countryId) AND (:statusId IS NULL OR s.studentStudyStatusId = :statusId) AND s.studyCountryIn IN :studyCountryIds")
       List<Integer> findStudentIdsByOptionalFiltersInStudyCountries(@Param("countryId") Integer countryId,
                                                                    @Param("statusId") Integer statusId,
                                                                    @Param("studyCountryIds") Collection<Integer> studyCountryIds);

       // Field-level filters: student ID (exact), name (contains), passport (contains)
       @EntityGraph(attributePaths = {"StudentCountry"})
       @Query("SELECT s FROM StudentDatum s WHERE " +
                     "(:studentId IS NULL OR s.studentId = :studentId) AND " +
                     "(:name IS NULL OR LOWER(s.studentName) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
                     "(:passport IS NULL OR LOWER(s.studentPassportNumber) LIKE LOWER(CONCAT('%', :passport, '%')))")
       Page<StudentDatum> findByFieldFilters(
                     @Param("studentId") Integer studentId,
                     @Param("name") String name,
                     @Param("passport") String passport,
                     Pageable pageable);

       /** The name on file, for child rows that carry a copy of it (the fee list). */
       @Query("SELECT s.studentName FROM StudentDatum s WHERE s.id = :studentId")
       Optional<String> findStudentNameById(@Param("studentId") Integer studentId);

       /** Efficient country lookup for by-ID scope validation in child entities. */
       @Query("SELECT s.studyCountryIn FROM StudentDatum s WHERE s.id = :studentId")
       Optional<Integer> findStudyCountryInByStudentId(@Param("studentId") Integer studentId);

       // Field-level filters combined with exact status filter
       @EntityGraph(attributePaths = {"StudentCountry"})
       @Query("SELECT s FROM StudentDatum s WHERE " +
                     "s.studentStudyStatusId = :statusId AND " +
                     "(:studentId IS NULL OR s.studentId = :studentId) AND " +
                     "(:name IS NULL OR LOWER(s.studentName) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
                     "(:passport IS NULL OR LOWER(s.studentPassportNumber) LIKE LOWER(CONCAT('%', :passport, '%')))")
       Page<StudentDatum> findByFieldFiltersAndStatus(
                     @Param("statusId") Integer statusId,
                     @Param("studentId") Integer studentId,
                     @Param("name") String name,
                     @Param("passport") String passport,
                     Pageable pageable);

}
