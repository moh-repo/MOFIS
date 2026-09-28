package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuVisibilityFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuVisibilityFilterRepository extends JpaRepository<LuVisibilityFilter, Integer> {

    List<LuVisibilityFilter> findByIsDeletedFalseOrderBySortOrderAsc();
}
