package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyPayFeeInStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyPayFeeInStatusRepository extends JpaRepository<StudyPayFeeInStatus, Integer> {
} 