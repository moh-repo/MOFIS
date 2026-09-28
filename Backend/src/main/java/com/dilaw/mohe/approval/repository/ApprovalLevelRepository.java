package com.dilaw.mohe.approval.repository;

import com.dilaw.mohe.approval.model.ApprovalLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalLevelRepository extends JpaRepository<ApprovalLevel, Long> {

    List<ApprovalLevel> findByConfigIdOrderByLevelOrderAsc(Long configId);

    List<ApprovalLevel> findByConfigIdAndIsActiveTrueOrderByLevelOrderAsc(Long configId);

    Optional<ApprovalLevel> findByConfigIdAndLevelOrder(Long configId, Integer levelOrder);

    List<ApprovalLevel> findByRequiredRoleId(Long roleId);
}
