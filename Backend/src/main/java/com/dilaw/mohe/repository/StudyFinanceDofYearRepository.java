package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyFinanceDofYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyFinanceDofYearRepository extends JpaRepository<StudyFinanceDofYear, Integer> {
} 