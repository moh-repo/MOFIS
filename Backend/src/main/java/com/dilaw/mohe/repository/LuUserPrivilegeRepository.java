package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuUserPrivilege;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuUserPrivilegeRepository extends JpaRepository<LuUserPrivilege, Integer> {

    List<LuUserPrivilege> findByIsDeletedFalseOrderBySortOrderAsc();
}
