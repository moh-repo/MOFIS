package com.dilaw.mohe.approval.repository;

import com.dilaw.mohe.approval.model.ApprovalConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ApprovalConfigRepository extends JpaRepository<ApprovalConfig, Long> {

    Optional<ApprovalConfig> findByEntityType(String entityType);

    Optional<ApprovalConfig> findByEntityTypeAndIsActiveTrue(String entityType);

    List<ApprovalConfig> findByIsActiveTrue();

    boolean existsByEntityType(String entityType);
}
