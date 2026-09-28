package com.dilaw.mohe.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentParentDto {
    private Integer parentId;
    private Integer studentId;
    private Integer relationshipId;
    private String relationshipName;
    private String parentName;
    private String nationalId;
    private String occupation;
    private String phone;
    private String address;
    private Boolean isAlive;
    private String notes;
}
