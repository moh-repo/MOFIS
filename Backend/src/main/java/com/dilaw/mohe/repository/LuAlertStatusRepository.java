package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.LuAlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LuAlertStatusRepository extends JpaRepository<LuAlertStatus, Integer> {
}
