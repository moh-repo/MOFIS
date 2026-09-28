package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.PaymentAuth;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentAuthRepository extends JpaRepository<PaymentAuth, Integer>,
        JpaSpecificationExecutor<PaymentAuth> {

    List<PaymentAuth> findByStudentIdOrderByAuthMidateDesc(Integer studentId);

    List<PaymentAuth> findByAuthStatus(String status);

    @Query("""
        SELECT p FROM PaymentAuth p
        WHERE (:searchTerm IS NULL OR :searchTerm = ''
               OR p.studentName LIKE %:searchTerm%
               OR p.authFormNumber LIKE %:searchTerm%
               OR p.recipientName LIKE %:searchTerm%
               OR p.ministryName LIKE %:searchTerm%)
          AND (:status IS NULL OR p.authStatus = :status)
        """)
    Page<PaymentAuth> search(@Param("searchTerm") String searchTerm,
                             @Param("status") String status,
                             Pageable pageable);
}
