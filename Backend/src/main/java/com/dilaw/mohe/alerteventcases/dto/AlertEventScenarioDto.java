package com.dilaw.mohe.alerteventcases.dto;

import com.dilaw.mohe.alerteventcases.model.AlertEventScenario;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class AlertEventScenarioDto {

    private Integer scenarioId;
    private String scenarioCode;
    private String scenarioNameEn;
    private String scenarioNameAr;
    private String descriptionEn;
    private String descriptionAr;
    private String severity;
    private Integer thresholdDays;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public static AlertEventScenarioDto from(AlertEventScenario s) {
        AlertEventScenarioDto dto = new AlertEventScenarioDto();
        dto.scenarioId = s.getScenarioId();
        dto.scenarioCode = s.getScenarioCode();
        dto.scenarioNameEn = s.getScenarioNameEn();
        dto.scenarioNameAr = s.getScenarioNameAr();
        dto.descriptionEn = s.getDescriptionEn();
        dto.descriptionAr = s.getDescriptionAr();
        dto.severity = s.getSeverity();
        dto.thresholdDays = s.getThresholdDays();
        dto.isActive = s.getIsActive();
        dto.createdAt = s.getCreatedAt();
        dto.updatedAt = s.getUpdatedAt();
        return dto;
    }
}
