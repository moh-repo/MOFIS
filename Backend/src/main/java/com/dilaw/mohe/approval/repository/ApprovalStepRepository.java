package com.dilaw.mohe.approval.repository;

import com.dilaw.mohe.approval.model.ApprovalStep;
import com.dilaw.mohe.approval.model.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {

    List<ApprovalStep> findByRequestIdOrderByLevelOrderAsc(Long requestId);

    Optional<ApprovalStep> findByRequestIdAndLevelOrder(Long requestId, Integer levelOrder);

    List<ApprovalStep> findByRequestIdAndStatus(Long requestId, ApprovalStatus status);

    Optional<ApprovalStep> findFirstByRequestIdAndStatusOrderByLevelOrderAsc(Long requestId, ApprovalStatus status);

    /** Every step of these requests with its level, in one query (review A1: no query per request). */
    @Query("SELECT s FROM ApprovalStep s LEFT JOIN FETCH s.level WHERE s.request.id IN :requestIds")
    List<ApprovalStep> findWithLevelByRequestIdIn(@Param("requestIds") Collection<Long> requestIds);
}
