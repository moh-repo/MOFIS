package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentCurrentLevel;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentYearResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FIX-PLAN P5-8, decision D24-a (26 Sep 2026): progress counts only the years whose
 * result someone recorded. Every student approved since onboarding has one placeholder
 * row per planned year, seeded "passed" with no academic year and no grade; counted as
 * results, they put every such student at 100 %.
 */
class StudentProgressFromResultsTest {

    private static final int STUDENT = 4;

    private final StudentDataService students = mock(StudentDataService.class);
    private final StudentCurrentLevelService levels = mock(StudentCurrentLevelService.class);
    private final StudentYearResultService results = mock(StudentYearResultService.class);
    private final StudentProgressService service = new StudentProgressService();

    @BeforeEach
    void firstYearOfFive() {
        ReflectionTestUtils.setField(service, "studentDataService", students);
        ReflectionTestUtils.setField(service, "studentCurrentLevelService", levels);
        ReflectionTestUtils.setField(service, "studentYearResultService", results);
        StudentDatum student = new StudentDatum();
        student.setStudentId(STUDENT);
        student.setStudentCurrentLevelId(1);
        student.setStudentStudyNoOfYears(5);
        StudentCurrentLevel first = new StudentCurrentLevel();
        first.setStudentCurrentLevelName("السنة الأولى");
        first.setStudentCurrentLevelNameEn("First Year");
        first.setStudentCurrentLevelOrderNo(1);
        when(students.getStudentData(STUDENT)).thenReturn(student);
        when(levels.getById(1)).thenReturn(first);
    }

    /** A seeded placeholder: passed, stamped with a study status, no year and no grade. */
    private static StudentYearResult placeholder(int year) {
        StudentYearResult r = new StudentYearResult();
        r.setYearNumber(year);
        r.setPassed(true);
        r.setStudyYearStatusId(2);
        return r;
    }

    private static StudentYearResult recorded(int year, String academicYear, boolean passed) {
        StudentYearResult r = placeholder(year);
        r.setAcademicYear(academicYear);
        r.setPassed(passed);
        return r;
    }

    @Test
    @DisplayName("placeholders only: not a result, so progress is the current level (1 of 5 = 20 %), not 100 %")
    void placeholdersAreNotResults() {
        when(results.getByStudentId(STUDENT)).thenReturn(List.of(placeholder(1), placeholder(2), placeholder(3),
                placeholder(4), placeholder(5)));

        assertThat(service.getProgress(STUDENT).getProgressPercent()).isEqualTo(20);
        assertThat(placeholder(1).isRecorded()).as("a status alone is the seed's, not a result").isFalse();
    }

    @Test
    @DisplayName("recorded years decide: two passed of five = 40 %; a recorded failed year does not count")
    void recordedYearsDecide() {
        when(results.getByStudentId(STUDENT)).thenReturn(List.of(recorded(1, "2023/2024", true),
                recorded(2, "2024/2025", true), recorded(3, "2025/2026", false), placeholder(4), placeholder(5)));

        assertThat(service.getProgress(STUDENT).getProgressPercent()).isEqualTo(40);
        assertThat(service.getProgress(STUDENT).getCurrentYearOrder()).as("the level still names the year").isEqualTo(1);
    }

    @Test
    @DisplayName("a grade alone, or a failed mark, is a recorded result; never above 100 %")
    void whatCountsAsRecorded() {
        StudentYearResult graded = placeholder(1);
        graded.setGrade("B+");
        StudentYearResult failed = placeholder(2);
        failed.setPassed(false);
        assertThat(graded.isRecorded()).isTrue();
        assertThat(failed.isRecorded()).isTrue();

        when(results.getByStudentId(STUDENT)).thenReturn(List.of(recorded(1, "a", true), recorded(1, "a", true),
                recorded(2, "b", true), recorded(3, "c", true), recorded(4, "d", true), recorded(5, "e", true),
                recorded(6, "f", true)));
        assertThat(service.getProgress(STUDENT).getProgressPercent()).isEqualTo(100);
    }
}
