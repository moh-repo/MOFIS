package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuPermissionResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuPermissionResourceTypeRepository extends JpaRepository<LuPermissionResourceType, Integer> {

    List<LuPermissionResourceType> findByIsDeletedFalseOrderBySortOrderAsc();
}
