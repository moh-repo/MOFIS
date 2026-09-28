package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuDeathProcedure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuDeathProcedureRepository extends JpaRepository<LuDeathProcedure, Integer> {

    List<LuDeathProcedure> findByIsDeletedFalseOrderBySortOrderAsc();
}
