package com.dilaw.mohe.alerteventcases.service;

import com.dilaw.mohe.codes.AlertCaseStatus;

import com.dilaw.mohe.alerteventcases.model.AlertEventCase;
import com.dilaw.mohe.alerteventcases.model.AlertEventScenario;
import com.dilaw.mohe.alerteventcases.repository.AlertEventCaseRepository;
import com.dilaw.mohe.alerteventcases.repository.AlertEventScenarioRepository;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.repository.StudentDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Scans active students against each enabled scenario and creates
 * ALERT_EVENT_CASE rows where none already exist in OPEN/ACKNOWLEDGED status.
 * Runs without a user security context (scheduler/system job).
 *
 * <p>Each scenario is two queries: one that selects the students it applies to, and
 * one that says which of them already have an open case. It used to be seven or more
 * — {@code studentRepo.findAll()} for the scenario, plus an existence query for every
 * student it returned — with the whole student table read into memory six times a
 * night and filtered in Java. The selection now happens in SQL, where an index can
 * help it; see the alert-detection block in {@link StudentDataRepository}.
 */
@Service
public class AlertEventDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AlertEventDetectionService.class);
    private static final List<String> ACTIVE_STATUSES = List.of(AlertCaseStatus.OPEN, AlertCaseStatus.ACKNOWLEDGED);

    @Autowired
    private AlertEventScenarioRepository scenarioRepo;

    @Autowired
    private AlertEventCaseRepository caseRepo;

    @Autowired
    private StudentDataRepository studentRepo;

    @Transactional
    public int runAllScenarios() {
        List<AlertEventScenario> scenarios = scenarioRepo.findAllByIsActiveTrueOrderByScenarioId();
        int total = 0;
        for (AlertEventScenario scenario : scenarios) {
            try {
                total += detectForScenario(scenario);
            } catch (Exception e) {
                log.error("Alert detection failed for scenario {}: {}", scenario.getScenarioCode(), e.getMessage(), e);
            }
        }
        log.info("Alert detection complete — {} new case(s) created", total);
        return total;
    }

    private int detectForScenario(AlertEventScenario scenario) {
        return switch (scenario.getScenarioCode()) {
            case "PASSPORT_EXPIRY_SOON" -> detectPassportExpirySoon(scenario);
            case "PASSPORT_EXPIRED"     -> detectPassportExpired(scenario);
            case "LAST_STUDY_YEAR"      -> detectLastStudyYear(scenario);
            case "LAST_QUARTER"         -> detectLastQuarter(scenario);
            case "STUDY_DURATION_EXCEEDED" -> detectStudyDurationExceeded(scenario);
            case "NO_FINANCE_RECORD"    -> detectNoFinanceRecord(scenario);
            default -> {
                log.warn("No detection logic for scenario code: {}", scenario.getScenarioCode());
                yield 0;
            }
        };
    }

    private int detectPassportExpirySoon(AlertEventScenario scenario) {
        int days = scenario.getThresholdDays() != null ? scenario.getThresholdDays() : 90;
        Instant now = Instant.now();
        Instant cutoff = now.plus(days, ChronoUnit.DAYS);
        return createCasesIfAbsent(scenario,
                studentRepo.findWithPassportExpiringBetween(now, cutoff),
                StudentDatum::getStudentPassportExpireDate);
    }

    private int detectPassportExpired(AlertEventScenario scenario) {
        return createCasesIfAbsent(scenario,
                studentRepo.findWithPassportExpiredBefore(Instant.now()),
                StudentDatum::getStudentPassportExpireDate);
    }

    private int detectLastStudyYear(AlertEventScenario scenario) {
        return createCasesIfAbsent(scenario, studentRepo.findAtFinalStudyYear(), s -> null);
    }

    private int detectStudyDurationExceeded(AlertEventScenario scenario) {
        return createCasesIfAbsent(scenario, studentRepo.findExceedingStudyDuration(), s -> null);
    }

    private int detectLastQuarter(AlertEventScenario scenario) {
        // Students whose studentLastQuarter is non-null and non-zero (meaning a final
        // quarter is registered). Cases are created once and staff resolve them when
        // the student completes or extends.
        return createCasesIfAbsent(scenario, studentRepo.findWithFinalQuarterRegistered(), s -> null);
    }

    private int detectNoFinanceRecord(AlertEventScenario scenario) {
        return createCasesIfAbsent(scenario, studentRepo.findWithoutFinanceRecord(), s -> null);
    }

    @FunctionalInterface
    private interface ReferenceDateExtractor {
        Instant extract(StudentDatum s);
    }

    /**
     * Creates a case for each student that does not already have an active one.
     *
     * <p>The "does not already have one" test used to be a query per student. It is now
     * a single query per scenario, read into a set before the loop: same answer, and the
     * nightly run stops making thousands of round trips to learn what it could have
     * asked once. The set is safe to compute up front because nothing inside the loop
     * can add a case for a student the loop has not reached — a scenario has at most one
     * active case per student, and {@code created} tracks the ones added here.
     */
    private int createCasesIfAbsent(AlertEventScenario scenario, List<StudentDatum> students,
                                     ReferenceDateExtractor dateExtractor) {
        Set<Integer> alreadyHaveCase =
                caseRepo.findStudentIdsWithActiveCase(scenario.getScenarioId(), ACTIVE_STATUSES);

        List<AlertEventCase> newCases = new ArrayList<>();
        Instant createdAt = Instant.now();
        for (StudentDatum student : students) {
            if (alreadyHaveCase.contains(student.getStudentId())) {
                continue;
            }
            AlertEventCase aec = new AlertEventCase();
            aec.setScenario(scenario);
            aec.setStudent(student);
            aec.setStudyCountryInId(student.getStudyCountryIn());
            aec.setReferenceDate(dateExtractor.extract(student));
            aec.setStatus(AlertCaseStatus.OPEN);
            aec.setCreatedAt(createdAt);
            newCases.add(aec);
        }

        if (!newCases.isEmpty()) {
            caseRepo.saveAll(newCases);
            log.info("Scenario [{}] — created {} new case(s)", scenario.getScenarioCode(), newCases.size());
        }
        return newCases.size();
    }
}
