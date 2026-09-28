package com.dilaw.mohe.dto;

import lombok.Data;

@Data
public class TracerFilterDto {
    /** Optional study-country scope (STUDY_COUNTRY_IN); validated against UMS UserCountries. */
    private Integer countryId;
    private String university;
    private String major;
    private String country;
    private String employmentStatus;
    private String degreeLevel;
    private String connectionType;
    private Integer minConnectionStrength;
}

