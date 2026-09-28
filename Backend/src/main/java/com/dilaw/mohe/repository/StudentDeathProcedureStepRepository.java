package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentDeathProcedureStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentDeathProcedureStepRepository extends JpaRepository<StudentDeathProcedureStep, Integer> {

    List<StudentDeathProcedureStep> findByDeathCaseIdOrderByStepIdAsc(Integer deathCaseId);

    void deleteByDeathCaseId(Integer deathCaseId);
}
