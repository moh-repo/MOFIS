package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyStatusRepository extends JpaRepository<StudyStatus, Integer> {
} 