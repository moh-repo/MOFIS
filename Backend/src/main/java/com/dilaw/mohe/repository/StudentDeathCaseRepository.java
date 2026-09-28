package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentDeathCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentDeathCaseRepository extends JpaRepository<StudentDeathCase, Integer> {

    Optional<StudentDeathCase> findByStudentIdAndIsDeletedFalse(Integer studentId);
}
