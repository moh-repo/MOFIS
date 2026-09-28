package com.dilaw.mohe.approval.repository;

import com.dilaw.mohe.approval.model.ApprovalLevelApprover;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalLevelApproverRepository extends JpaRepository<ApprovalLevelApprover, Long> {

    List<ApprovalLevelApprover> findByLevelId(Long levelId);

    List<ApprovalLevelApprover> findByUserId(Long userId);

    void deleteByLevelId(Long levelId);
}
