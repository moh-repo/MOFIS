package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyFinReceivedStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyFinReceivedStatusRepository extends JpaRepository<StudyFinReceivedStatus, Integer> {
} 