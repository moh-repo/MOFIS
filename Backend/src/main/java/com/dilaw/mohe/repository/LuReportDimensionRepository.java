package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuReportDimension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuReportDimensionRepository extends JpaRepository<LuReportDimension, Integer> {

    List<LuReportDimension> findByGroupCodeAndIsDeletedFalseOrderBySortOrderAsc(String groupCode);

    List<LuReportDimension> findByIsDeletedFalseOrderBySortOrderAsc();
}
