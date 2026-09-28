package com.dilaw.mohe.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentRelativeDto {
    private Integer relativeId;
    private Integer studentId;
    private Integer relatedStudentId;
    private String relatedStudentName;
    private Integer relatedStudentStatusId;
    private String relatedStudentStatusName;
    private Integer relatedStudentCurrentLevelId;
    private String relatedStudentCurrentLevelName;
    private Integer relationshipId;
    private String relationshipName;
    private String notes;
}
