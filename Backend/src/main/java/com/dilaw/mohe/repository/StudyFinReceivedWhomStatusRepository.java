package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyFinReceivedWhomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyFinReceivedWhomStatusRepository extends JpaRepository<StudyFinReceivedWhomStatus, Integer> {
} 