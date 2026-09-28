package com.dilaw.mohe.alerteventcases.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlertEventScenarioRequest {

    private String scenarioCode;
    private String scenarioNameEn;
    private String scenarioNameAr;
    private String descriptionEn;
    private String descriptionAr;
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL
    private Integer thresholdDays;
    private Boolean isActive;
}
