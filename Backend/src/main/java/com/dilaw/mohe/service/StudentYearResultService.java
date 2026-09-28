package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentYearResult;
import com.dilaw.mohe.repository.StudentYearResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Transactional(readOnly = true)
@Service
public class StudentYearResultService {

    @Autowired
    private StudentYearResultRepository repository;

    public List<StudentYearResult> getByStudentId(Integer studentId) {
        return repository.findByStudentIdOrderByYearNumberAsc(studentId);
    }

    /**
     * Seed one row per study year after student onboarding if none exist.
     */
    @Transactional
    public void ensureInitialYears(Integer studentId, Integer plannedYears, Integer defaultStudyYearStatusId) {
        if (studentId == null || repository.countByStudentId(studentId) > 0) {
            return;
        }
        int n = (plannedYears != null && plannedYears > 0) ? plannedYears : 1;
        Instant now = Instant.now();
        java.util.ArrayList<StudentYearResult> rows = new java.util.ArrayList<>();
        for (int y = 1; y <= n; y++) {
            StudentYearResult r = new StudentYearResult();
            r.setStudentId(studentId);
            r.setYearNumber(y);
            r.setPassed(true);
            if (defaultStudyYearStatusId != null) {
                r.setStudyYearStatusId(defaultStudyYearStatusId);
            }
            r.setUpdatedAt(now);
            rows.add(r);
        }
        repository.saveAll(rows);
    }

    @Transactional
    public List<StudentYearResult> replaceForStudent(Integer studentId, List<StudentYearResult> results) {
        repository.deleteByStudentId(studentId);
        repository.flush(); // force DELETE to DB before INSERTs to avoid unique-key collision
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        for (StudentYearResult r : results) {
            r.setId(null);
            r.setStudentId(studentId);
            r.setUpdatedAt(now);
        }
        return repository.saveAll(results);
    }

    /**
     * Returns true when the student has at least one year result marked as failed.
     * Used as a soft guard before authorizing finance payments.
     */
    public boolean hasFailingYearResult(Integer studentId) {
        if (studentId == null) return false;
        return repository.existsByStudentIdAndPassedFalse(studentId);
    }

    /**
     * Returns a human-readable warning message if the student's latest year result
     * is a failure, or null if the most recent result is passing (or none exist).
     */
    public String getPaymentWarning(Integer studentId) {
        if (studentId == null) return null;
        return repository.findTopByStudentIdOrderByYearNumberDesc(studentId)
                .filter(r -> Boolean.FALSE.equals(r.getPassed()))
                .map(r -> "Student " + studentId + " failed year " + r.getYearNumber()
                        + " — verify eligibility before payment.")
                .orElse(null);
    }
}
