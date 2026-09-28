package com.dilaw.mohe.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class StudentDeathCaseDto {
    private Integer deathCaseId;
    private Integer studentId;
    private LocalDate dateOfDeath;
    private Integer levelAtDeathId;
    private String levelAtDeathName;
    private Integer studyYearAtDeath;
    private String placeOfDeath;
    private String cause;
    private LocalDateTime reportedDate;
    private String reportedBy;
    private String caseStatus;
    private String notes;
    private List<StudentDeathProcedureStepDto> steps = new ArrayList<>();
}
