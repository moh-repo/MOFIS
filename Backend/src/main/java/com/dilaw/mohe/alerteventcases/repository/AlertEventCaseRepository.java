package com.dilaw.mohe.alerteventcases.repository;

import com.dilaw.mohe.alerteventcases.model.AlertEventCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AlertEventCaseRepository extends JpaRepository<AlertEventCase, Integer>,
        JpaSpecificationExecutor<AlertEventCase> {

    @Override
    @EntityGraph(attributePaths = {"scenario", "student"})
    Page<AlertEventCase> findAll(Specification<AlertEventCase> spec, Pageable pageable);

    Optional<AlertEventCase> findByScenario_ScenarioIdAndStudent_StudentIdAndStatusIn(
            Integer scenarioId, Integer studentId, java.util.List<String> statuses);

    /**
     * The students that already have an open or acknowledged case for this scenario.
     *
     * <p>Replaces calling {@link #findByScenario_ScenarioIdAndStudent_StudentIdAndStatusIn}
     * once per candidate student. The nightly run asked that question for every student
     * of every scenario — with the six scenarios shipped and the ~940 students in the
     * development database, roughly 5,600 round trips to answer what one query answers.
     * A scenario has at most one active case per student, so the id set is small.
     */
    @Query("SELECT c.student.studentId FROM AlertEventCase c "
            + "WHERE c.scenario.scenarioId = :scenarioId AND c.status IN :statuses")
    java.util.Set<Integer> findStudentIdsWithActiveCase(@Param("scenarioId") Integer scenarioId,
                                                        @Param("statuses") java.util.List<String> statuses);
}
