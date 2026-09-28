package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentBooksAllowance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentBooksAllowanceRepository extends JpaRepository<StudentBooksAllowance, Integer>,
        JpaSpecificationExecutor<StudentBooksAllowance> {

    List<StudentBooksAllowance> findByStudentIdOrderByBooksAllowIdAsc(Integer studentId);
}
