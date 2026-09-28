package com.dilaw.mohe.alerteventcases.repository;

import com.dilaw.mohe.alerteventcases.model.AlertEventScenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertEventScenarioRepository extends JpaRepository<AlertEventScenario, Integer> {

    List<AlertEventScenario> findAllByIsActiveTrueOrderByScenarioId();

    Optional<AlertEventScenario> findByScenarioCode(String scenarioCode);
}
