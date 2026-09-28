package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.StudentProgressDto;
import com.dilaw.mohe.model.StudentCurrentLevel;
import com.dilaw.mohe.model.StudentDatum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Current Year in both languages (QA B1, STD-007): an English profile read
 * "Current Level: Second Year" beside "Current Year: السنة الثانية", because the
 * label came from the Arabic column only.
 */
class ProgressYearLabelTest {

    private final StudentDataService students = mock(StudentDataService.class);
    private final StudentCurrentLevelService levels = mock(StudentCurrentLevelService.class);
    private final StudentProgressService service = new StudentProgressService();

    private StudentProgressDto progressAt(String nameAr, String nameEn) {
        ReflectionTestUtils.setField(service, "studentDataService", students);
        ReflectionTestUtils.setField(service, "studentCurrentLevelService", levels);
        // No recorded year results: progress falls back to the current level (P5-8).
        ReflectionTestUtils.setField(service, "studentYearResultService", mock(StudentYearResultService.class));
        StudentDatum student = new StudentDatum();
        student.setStudentId(4);
        student.setStudentCurrentLevelId(2);
        student.setStudentStudyNoOfYears(4);
        StudentCurrentLevel level = new StudentCurrentLevel();
        level.setStudentCurrentLevelName(nameAr);
        level.setStudentCurrentLevelNameEn(nameEn);
        level.setStudentCurrentLevelOrderNo(2);
        when(students.getStudentData(4)).thenReturn(student);
        when(levels.getById(2)).thenReturn(level);
        return service.getProgress(4);
    }

    @Test
    @DisplayName("sends the year in both languages")
    void bothLanguages() {
        StudentProgressDto p = progressAt("السنة الثانية", "Second Year");

        assertThat(p.getCurrentYearLabel()).isEqualTo("السنة الثانية");
        assertThat(p.getCurrentYearLabelEn()).isEqualTo("Second Year");
        assertThat(p.getProgressPercent()).isEqualTo(50);
    }

    @Test
    @DisplayName("an untranslated level still has an English label")
    void untranslatedLevel() {
        assertThat(progressAt("السنة الثانية", null).getCurrentYearLabelEn()).isEqualTo("Year 2");
    }
}
