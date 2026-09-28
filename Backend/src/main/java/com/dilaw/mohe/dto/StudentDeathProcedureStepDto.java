package com.dilaw.mohe.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class StudentDeathProcedureStepDto {
    private Integer stepId;
    private Integer deathCaseId;
    private Integer procedureId;
    private String procedureCode;
    private String procedureNameAr;
    private String procedureNameEn;
    private Integer sortOrder;
    private Boolean isCompleted;
    private LocalDateTime completedDate;
    private String completedBy;
    private String notes;
}
