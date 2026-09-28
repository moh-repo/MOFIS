package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuPermissionActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuPermissionActionTypeRepository extends JpaRepository<LuPermissionActionType, Integer> {

    List<LuPermissionActionType> findByIsDeletedFalseOrderBySortOrderAsc();
}
