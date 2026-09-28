package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuFeeScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuFeeScopeRepository extends JpaRepository<LuFeeScope, Integer> {
    List<LuFeeScope> findByScopeTypeOrderBySortOrderAsc(String scopeType);
}
