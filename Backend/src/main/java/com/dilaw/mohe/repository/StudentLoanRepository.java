package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentLoan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentLoanRepository extends JpaRepository<StudentLoan, Integer>,
        JpaSpecificationExecutor<StudentLoan> {

    List<StudentLoan> findByStudentIdOrderByLoanIdAsc(Integer studentId);
}
