package com.dilaw.mohe.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentDeathProcedureStepUpdateRequest {
    private Boolean isCompleted;
    private String notes;
}
