package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuBooleanFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuBooleanFilterRepository extends JpaRepository<LuBooleanFilter, Integer> {

    List<LuBooleanFilter> findByIsDeletedFalseOrderBySortOrderAsc();
}
