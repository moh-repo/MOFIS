package com.dilaw.mohe.approval.repository;

import com.dilaw.mohe.approval.model.ApprovalHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {

    @Query("SELECT h FROM ApprovalHistory h LEFT JOIN FETCH h.actor WHERE h.request.id = :requestId ORDER BY h.actionAt DESC")
    List<ApprovalHistory> findByRequestIdOrderByActionAtDesc(@Param("requestId") Long requestId);

    Page<ApprovalHistory> findByRequestId(Long requestId, Pageable pageable);

    List<ApprovalHistory> findByActionBy(Long userId);

    Page<ApprovalHistory> findByActionBy(Long userId, Pageable pageable);
}
