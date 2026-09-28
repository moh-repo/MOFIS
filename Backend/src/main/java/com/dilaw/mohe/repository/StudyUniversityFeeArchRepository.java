package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyUniversityFeeArch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudyUniversityFeeArchRepository extends JpaRepository<StudyUniversityFeeArch, Integer>,
        JpaSpecificationExecutor<StudyUniversityFeeArch> {
    
    // Find all records for a specific student
    List<StudyUniversityFeeArch> findByStudentId(Integer studentId);
    
    // Find records by student name
    @Query("SELECT s FROM StudyUniversityFeeArch s WHERE s.studyUniversityFeeStudentName LIKE %:studentName%")
    List<StudyUniversityFeeArch> findByStudentNameContaining(@Param("studentName") String studentName);
    
    // Find records by payment status
    List<StudyUniversityFeeArch> findByStudyUniversityFeePayStatus(Integer payStatus);
    
    // Find records by year
    List<StudyUniversityFeeArch> findByStudyUniversityFeeYear(String year);
    
    // Find distinct students with their names from the fee table itself
    @Query("SELECT DISTINCT s.studentId, s.studyUniversityFeeStudentName FROM StudyUniversityFeeArch s " +
           "ORDER BY s.studentId")
    List<Object[]> findDistinctStudentsWithNames();
    
    // Find all fee records for a specific student
    @Query("SELECT s FROM StudyUniversityFeeArch s WHERE s.studentId = :studentId ORDER BY s.studyUniversityFeeYear DESC")
    List<StudyUniversityFeeArch> findByStudentIdOrdered(@Param("studentId") Integer studentId);
}
