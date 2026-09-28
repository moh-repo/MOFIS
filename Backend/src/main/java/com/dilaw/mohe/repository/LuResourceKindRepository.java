package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuResourceKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuResourceKindRepository extends JpaRepository<LuResourceKind, Integer> {

    List<LuResourceKind> findByIsDeletedFalseOrderBySortOrderAsc();
}
