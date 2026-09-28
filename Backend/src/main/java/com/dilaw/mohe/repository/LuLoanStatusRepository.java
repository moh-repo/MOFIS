package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuLoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuLoanStatusRepository extends JpaRepository<LuLoanStatus, Integer> {

    List<LuLoanStatus> findByIsDeletedFalseOrderBySortOrderAsc();
}
