package com.dilaw.mohe.wafar.repository;

import com.dilaw.mohe.wafar.model.WafarStudentFeePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WafarStudentFeePaymentRepository extends JpaRepository<WafarStudentFeePayment, Integer> {

    List<WafarStudentFeePayment> findByStudentId(Integer studentId);

    List<WafarStudentFeePayment> findBySpendingId(Integer spendingId);
}
