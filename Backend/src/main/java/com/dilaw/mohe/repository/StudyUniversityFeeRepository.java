package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyUniversityFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyUniversityFeeRepository extends JpaRepository<StudyUniversityFee, Integer>,
        JpaSpecificationExecutor<StudyUniversityFee> {

    java.util.List<StudyUniversityFee> findByStudentId(Integer studentId);

    java.util.Optional<StudyUniversityFee> findTopByStudentIdOrderByStudyUniversityFeeMidateDesc(Integer studentId);

    /**
     * Active fee for same student + academic year + semester number + semester coverage + year coverage
     * (excludes cancelled pay status 3).
     */
    @Query("""
            SELECT COUNT(f) FROM StudyUniversityFee f
            WHERE f.studentId = :studentId
            AND f.studyUniversityFeeYear = :feeYear
            AND f.studyUniversityFeeSemesterNumber = :semesterNumber
            AND f.studyUniversityFeeSemesterScopeId = :semesterScopeId
            AND f.studyUniversityFeeYearScopeId = :yearScopeId
            AND (f.studyUniversityFeePayStatus IS NULL OR f.studyUniversityFeePayStatus <> 3)
            AND (:excludeId IS NULL OR f.id <> :excludeId)
            """)
    long countActiveDuplicatePeriod(
            @Param("studentId") Integer studentId,
            @Param("feeYear") String feeYear,
            @Param("semesterNumber") Integer semesterNumber,
            @Param("semesterScopeId") Integer semesterScopeId,
            @Param("yearScopeId") Integer yearScopeId,
            @Param("excludeId") Integer excludeId);
} 