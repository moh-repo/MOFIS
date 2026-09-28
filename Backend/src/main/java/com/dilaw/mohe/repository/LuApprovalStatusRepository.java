package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuApprovalStatusRepository extends JpaRepository<LuApprovalStatus, Integer> {

    List<LuApprovalStatus> findByIsDeletedFalseOrderBySortOrderAsc();
}
