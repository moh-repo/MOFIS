package com.dilaw.mohe.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentRelativeSearchDto {
    private Integer studentId;
    private String studentName;
    private String passportNumber;
    private Integer studyStatusId;
    private String studyStatusName;
    private Integer currentLevelId;
    private String currentLevelName;
}
