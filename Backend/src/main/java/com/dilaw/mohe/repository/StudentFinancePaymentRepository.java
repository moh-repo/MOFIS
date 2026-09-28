package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentFinancePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentFinancePaymentRepository extends JpaRepository<StudentFinancePayment, Long> {

    List<StudentFinancePayment> findByStudentIdOrderByPaymentIdAsc(Integer studentId);

    /** Whether quarter close has already written this student ledger row for the quarter. */
    boolean existsByStudentIdAndMoheQuarterId(Integer studentId, Integer moheQuarterId);
}
