package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentYearResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentYearResultRepository extends JpaRepository<StudentYearResult, Integer> {

    List<StudentYearResult> findByStudentIdOrderByYearNumberAsc(Integer studentId);

    void deleteByStudentId(Integer studentId);

    long countByStudentId(Integer studentId);

    /** Returns true if the student has any year result where passed = false. */
    boolean existsByStudentIdAndPassedFalse(Integer studentId);

    /** Latest year result for the student (highest yearNumber). */
    java.util.Optional<StudentYearResult> findTopByStudentIdOrderByYearNumberDesc(Integer studentId);
}
