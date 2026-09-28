package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuTrendClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuTrendClassRepository extends JpaRepository<LuTrendClass, Integer> {

    List<LuTrendClass> findByCategoryAndIsDeletedFalseOrderBySortOrderAsc(String category);

    List<LuTrendClass> findByIsDeletedFalseOrderBySortOrderAsc();
}
