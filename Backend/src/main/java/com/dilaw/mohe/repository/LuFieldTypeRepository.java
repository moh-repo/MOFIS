package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuFieldType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuFieldTypeRepository extends JpaRepository<LuFieldType, Integer> {

    List<LuFieldType> findByIsDeletedFalseOrderBySortOrderAsc();
}
