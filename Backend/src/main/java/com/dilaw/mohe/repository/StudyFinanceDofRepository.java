package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudyFinanceDof;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyFinanceDofRepository extends JpaRepository<StudyFinanceDof, Integer> {
}
