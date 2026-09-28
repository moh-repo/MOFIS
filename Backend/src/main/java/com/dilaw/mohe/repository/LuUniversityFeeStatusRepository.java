package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuUniversityFeeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LuUniversityFeeStatusRepository extends JpaRepository<LuUniversityFeeStatus, Integer> {
}
