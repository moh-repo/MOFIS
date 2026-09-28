package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuFeeCheckPayor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LuFeeCheckPayorRepository extends JpaRepository<LuFeeCheckPayor, Integer> {
}
