package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuBooksAllowancePayStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuBooksAllowancePayStatusRepository extends JpaRepository<LuBooksAllowancePayStatus, Integer> {

    List<LuBooksAllowancePayStatus> findByIsDeletedFalseOrderBySortOrderAsc();
}
