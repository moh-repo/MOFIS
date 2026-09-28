package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuReportLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LuReportLabelRepository extends JpaRepository<LuReportLabel, Integer> {

    List<LuReportLabel> findByIsDeletedFalseOrderBySortOrderAsc();

    Optional<LuReportLabel> findByCodeAndIsDeletedFalse(String code);
}
