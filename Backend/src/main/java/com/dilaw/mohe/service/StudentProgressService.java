package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.StudentProgressDto;
import com.dilaw.mohe.model.AcademicPerformance;
import com.dilaw.mohe.model.StudentCurrentLevel;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentYearResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.http.HttpStatus;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class StudentProgressService {

    @Autowired
    private StudentDataService studentDataService;

    @Autowired
    private StudentCurrentLevelService studentCurrentLevelService;

    @Autowired
    private AcademicPerformanceService academicPerformanceService;

    @Autowired
    private StudentYearResultService studentYearResultService;

    /**
     * Build progress DTO for one student: current year label, order, total years, progress %, optional GPA and graduated.
     */
    public StudentProgressDto getProgress(Integer studentId) {
        return build(studentDataService.getStudentData(studentId));
    }

    /**
     * Progress for a student reading their own record through the portal.
     *
     * <p>Identical output; the only difference is which authorisation resolved
     * the student. See {@link StudentDataService#getStudentDataForPortal} — the
     * ministry path consults the UMS SecurityContext, which a portal student does
     * not have.
     */
    public StudentProgressDto getProgressForPortal(Integer studentId) {
        return build(studentDataService.getStudentDataForPortal(studentId));
    }

    private StudentProgressDto build(StudentDatum student) {
        Integer studentId = student.getStudentId();
        Integer levelId = student.getStudentCurrentLevelId();
        Integer totalYears = student.getStudentStudyNoOfYears() != null ? student.getStudentStudyNoOfYears() : 0;
        if (totalYears < 1) {
            totalYears = 1;
        }

        String currentYearLabel = null;
        String currentYearLabelEn = null;
        Integer currentYearOrder = 0;
        if (levelId != null && levelId != 0) {
            try {
                StudentCurrentLevel level = studentCurrentLevelService.getById(levelId);
                currentYearLabel = level.getStudentCurrentLevelName();
                currentYearLabelEn = level.getStudentCurrentLevelNameEn();
                Integer order = level.getStudentCurrentLevelOrderNo();
                currentYearOrder = order != null ? order : 0;
                if (currentYearLabelEn == null || currentYearLabelEn.isBlank()) {
                    currentYearLabelEn = "Year " + currentYearOrder;
                }
                if (currentYearLabel == null || currentYearLabel.isEmpty()) {
                    currentYearLabel = currentYearLabelEn;
                }
            } catch (ResponseStatusException e) {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    currentYearLabel = "Year ?";
                    currentYearLabelEn = "Year ?";
                } else {
                    throw e;
                }
            }
        }
        if (currentYearLabel == null) {
            currentYearLabel = "—";
        }
        if (currentYearLabelEn == null) {
            currentYearLabelEn = currentYearLabel;
        }

        // D24-a (FIX-PLAN P5-8): the years with a recorded result decide, when there are
        // any (passed recorded years of the planned years); otherwise the current level, as
        // before. The seeded placeholders are all "passed" and would read 100 %.
        List<StudentYearResult> recorded = studentYearResultService.getByStudentId(studentId).stream()
                .filter(StudentYearResult::isRecorded)
                .toList();
        int progressPercent = 0;
        if (!recorded.isEmpty()) {
            long passedYears = recorded.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getPassed()))
                    .map(StudentYearResult::getYearNumber)
                    .distinct()
                    .count();
            progressPercent = (int) Math.min(100, passedYears * 100 / totalYears);
        } else if (totalYears > 0 && currentYearOrder != null && currentYearOrder > 0) {
            progressPercent = Math.min(100, (currentYearOrder * 100) / totalYears);
        }

        return StudentProgressDto.builder()
                .studentId(studentId)
                .currentYearLabel(currentYearLabel)
                .currentYearLabelEn(currentYearLabelEn)
                .currentYearOrder(currentYearOrder)
                .totalYears(totalYears)
                .progressPercent(progressPercent)
                .build();
    }
}
