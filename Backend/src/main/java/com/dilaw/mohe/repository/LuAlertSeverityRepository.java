package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuAlertSeverity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LuAlertSeverityRepository extends JpaRepository<LuAlertSeverity, Integer> {
}
