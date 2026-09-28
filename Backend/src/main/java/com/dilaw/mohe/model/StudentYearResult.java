package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_YEAR_RESULT", schema = "MOHECORE")
public class StudentYearResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id", nullable = false)
    private Integer id;

    @Column(name = "StudentId", nullable = false)
    private Integer studentId;

    @Column(name = "YearNumber", nullable = false)
    private Integer yearNumber;

    @Nationalized
    @Column(name = "AcademicYear", length = 20)
    private String academicYear;

    @Column(name = "Passed", nullable = false)
    private Boolean passed = true;

    @Nationalized
    @Column(name = "Grade", length = 50)
    private String grade;

    @Nationalized
    @Column(name = "Notes", length = 500)
    private String notes;

    @Column(name = "StudyYearStatusId")
    private Integer studyYearStatusId;

    @Column(name = "MoheQuarterId")
    private Integer moheQuarterId;

    @Column(name = "UpdatedAt", nullable = false)
    private Instant updatedAt = Instant.now();

    /**
     * Whether someone recorded this year's result (FIX-PLAN P5-8, D24-a): an academic
     * year or a grade is entered, or the year is marked failed.
     *
     * <p>When a student is approved, {@code StudentYearResultService.ensureInitialYears}
     * seeds one row per planned year, marked passed ({@code Passed} is NOT NULL) and
     * stamped with the student's own study status, with no academic year and no grade.
     * Such a placeholder says nothing about the year, so neither "passed" nor the status
     * counts; the portal and the staff screens show it as "not recorded", and progress
     * does not count it. On dev (26 Sep 2026) 3 of 5 062 rows are recorded. Sent to the
     * screens as {@code recorded}.
     */
    public boolean isRecorded() {
        return (academicYear != null && !academicYear.isBlank())
                || (grade != null && !grade.isBlank())
                || Boolean.FALSE.equals(passed);
    }
}
