package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentNodeRepository extends JpaRepository<StudentNode, Integer> {

    Optional<StudentNode> findByStudentId(Integer studentId);

    List<StudentNode> findByIsActiveTrue();

    @Query("SELECT sn FROM StudentNode sn, StudentDatum sd WHERE sn.studentId = sd.studentId " +
           "AND sn.isActive = true " +
           "AND (:applyCountryFilter = false OR sd.studyCountryIn IN :countryIds)")
    List<StudentNode> findActiveByCountryScope(@Param("applyCountryFilter") boolean applyCountryFilter,
                                               @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT sn FROM StudentNode sn, StudentDatum sd WHERE sn.studentId = sd.studentId " +
           "AND sn.isActive = true " +
           "AND (:applyCountryFilter = false OR sd.studyCountryIn IN :countryIds) " +
           "AND (:university IS NULL OR sn.university LIKE %:university%) " +
           "AND (:major IS NULL OR sn.major LIKE %:major%) " +
           "AND (:country IS NULL OR sn.country LIKE %:country%) " +
           "AND (:employmentStatus IS NULL OR sn.currentEmploymentStatus = :employmentStatus) " +
           "AND (:degreeLevel IS NULL OR sn.degreeLevel = :degreeLevel)")
    List<StudentNode> searchNodes(@Param("applyCountryFilter") boolean applyCountryFilter,
                                  @Param("countryIds") List<Integer> countryIds,
                                  @Param("university") String university,
                                  @Param("major") String major,
                                  @Param("country") String country,
                                  @Param("employmentStatus") String employmentStatus,
                                  @Param("degreeLevel") String degreeLevel);

    @Query("SELECT DISTINCT sn.university FROM StudentNode sn, StudentDatum sd " +
           "WHERE sn.studentId = sd.studentId AND sn.isActive = true AND sn.university IS NOT NULL " +
           "AND (:applyCountryFilter = false OR sd.studyCountryIn IN :countryIds) " +
           "ORDER BY sn.university")
    List<String> findDistinctUniversities(@Param("applyCountryFilter") boolean applyCountryFilter,
                                          @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT DISTINCT sn.major FROM StudentNode sn, StudentDatum sd " +
           "WHERE sn.studentId = sd.studentId AND sn.isActive = true AND sn.major IS NOT NULL " +
           "AND (:applyCountryFilter = false OR sd.studyCountryIn IN :countryIds) " +
           "ORDER BY sn.major")
    List<String> findDistinctMajors(@Param("applyCountryFilter") boolean applyCountryFilter,
                                    @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT DISTINCT sn.country FROM StudentNode sn, StudentDatum sd " +
           "WHERE sn.studentId = sd.studentId AND sn.isActive = true AND sn.country IS NOT NULL " +
           "AND (:applyCountryFilter = false OR sd.studyCountryIn IN :countryIds) " +
           "ORDER BY sn.country")
    List<String> findDistinctCountries(@Param("applyCountryFilter") boolean applyCountryFilter,
                                       @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT DISTINCT sn.currentEmploymentStatus FROM StudentNode sn, StudentDatum sd " +
           "WHERE sn.studentId = sd.studentId AND sn.isActive = true AND sn.currentEmploymentStatus IS NOT NULL " +
           "AND (:applyCountryFilter = false OR sd.studyCountryIn IN :countryIds) " +
           "ORDER BY sn.currentEmploymentStatus")
    List<String> findDistinctEmploymentStatuses(@Param("applyCountryFilter") boolean applyCountryFilter,
                                                @Param("countryIds") List<Integer> countryIds);

    @Query("SELECT DISTINCT sn.degreeLevel FROM StudentNode sn, StudentDatum sd " +
           "WHERE sn.studentId = sd.studentId AND sn.isActive = true AND sn.degreeLevel IS NOT NULL " +
           "AND (:applyCountryFilter = false OR sd.studyCountryIn IN :countryIds) " +
           "ORDER BY sn.degreeLevel")
    List<String> findDistinctDegreeLevels(@Param("applyCountryFilter") boolean applyCountryFilter,
                                          @Param("countryIds") List<Integer> countryIds);
}
