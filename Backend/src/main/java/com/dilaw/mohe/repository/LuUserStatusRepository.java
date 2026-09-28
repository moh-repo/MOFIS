package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuUserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuUserStatusRepository extends JpaRepository<LuUserStatus, Integer> {

    List<LuUserStatus> findByIsDeletedFalseOrderBySortOrderAsc();
}
