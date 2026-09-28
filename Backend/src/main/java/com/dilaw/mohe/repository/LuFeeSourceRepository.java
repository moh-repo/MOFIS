package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuFeeSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LuFeeSourceRepository extends JpaRepository<LuFeeSource, Integer> {
}
