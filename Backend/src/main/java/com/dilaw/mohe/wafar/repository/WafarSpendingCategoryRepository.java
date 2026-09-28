package com.dilaw.mohe.wafar.repository;

import com.dilaw.mohe.wafar.model.WafarSpendingCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WafarSpendingCategoryRepository extends JpaRepository<WafarSpendingCategory, Integer> {

    List<WafarSpendingCategory> findByIsActiveTrue();

    Optional<WafarSpendingCategory> findByCategoryCode(String categoryCode);
}
