package com.dilaw.mohe.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class StudentDeathCaseRequest {
    private LocalDate dateOfDeath;
    private Integer levelAtDeathId;
    private Integer studyYearAtDeath;
    private String placeOfDeath;
    private String cause;
    private String notes;
}
