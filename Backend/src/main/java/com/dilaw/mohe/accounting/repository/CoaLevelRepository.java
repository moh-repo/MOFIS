package com.dilaw.mohe.accounting.repository;

import com.dilaw.mohe.accounting.model.CoaLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoaLevelRepository extends JpaRepository<CoaLevel, Integer> {
}
