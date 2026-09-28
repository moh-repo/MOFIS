package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuHttpMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuHttpMethodRepository extends JpaRepository<LuHttpMethod, Integer> {

    List<LuHttpMethod> findByIsDeletedFalseOrderBySortOrderAsc();
}
