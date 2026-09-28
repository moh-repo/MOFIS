package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuTravelFundingSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LuTravelFundingSourceRepository extends JpaRepository<LuTravelFundingSource, Integer> {

    @Query("SELECT f FROM LuTravelFundingSource f WHERE f.isActive = true ORDER BY f.displayOrder, f.fundingSourceNameEn")
    List<LuTravelFundingSource> findAllActiveOrderedByDisplay();

    Optional<LuTravelFundingSource> findByFundingSourceCode(String code);
}
