package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuStatusActive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LuStatusActiveRepository extends JpaRepository<LuStatusActive, Integer> {
}
