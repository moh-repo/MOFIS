package com.dilaw.mohe.accounting.repository;

import com.dilaw.mohe.accounting.model.FeeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeeTypeRepository extends JpaRepository<FeeType, Integer> {
}
