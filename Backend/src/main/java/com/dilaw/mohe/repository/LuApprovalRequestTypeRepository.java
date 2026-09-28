package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuApprovalRequestType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuApprovalRequestTypeRepository extends JpaRepository<LuApprovalRequestType, Integer> {

    List<LuApprovalRequestType> findByIsDeletedFalseOrderBySortOrderAsc();
}
