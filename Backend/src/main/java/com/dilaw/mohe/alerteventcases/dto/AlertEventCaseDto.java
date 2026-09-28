package com.dilaw.mohe.alerteventcases.dto;

import com.dilaw.mohe.alerteventcases.model.AlertEventCase;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class AlertEventCaseDto {

    private Integer caseId;
    private Integer scenarioId;
    private String scenarioCode;
    private String scenarioNameEn;
    private String scenarioNameAr;
    private String severity;
    private Integer studentId;
    private String studentName;
    private Integer studentCountryId;
    private Instant referenceDate;
    private String status;
    private String notes;
    private String acknowledgedBy;
    private Instant acknowledgedAt;
    private String resolvedBy;
    private Instant resolvedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static AlertEventCaseDto from(AlertEventCase c) {
        AlertEventCaseDto dto = new AlertEventCaseDto();
        dto.caseId = c.getCaseId();
        if (c.getScenario() != null) {
            dto.scenarioId = c.getScenario().getScenarioId();
            dto.scenarioCode = c.getScenario().getScenarioCode();
            dto.scenarioNameEn = c.getScenario().getScenarioNameEn();
            dto.scenarioNameAr = c.getScenario().getScenarioNameAr();
            dto.severity = c.getScenario().getSeverity();
        }
        if (c.getStudent() != null) {
            dto.studentId = c.getStudent().getStudentId();
            dto.studentName = c.getStudent().getStudentName();
        }
        // JSON key kept as studentCountryId so the API contract is unchanged; the
        // value is and always was the study country. See AlertEventCase.
        dto.studentCountryId = c.getStudyCountryInId();
        dto.referenceDate = c.getReferenceDate();
        dto.status = c.getStatus();
        dto.notes = c.getNotes();
        dto.acknowledgedBy = c.getAcknowledgedBy();
        dto.acknowledgedAt = c.getAcknowledgedAt();
        dto.resolvedBy = c.getResolvedBy();
        dto.resolvedAt = c.getResolvedAt();
        dto.createdAt = c.getCreatedAt();
        dto.updatedAt = c.getUpdatedAt();
        return dto;
    }
}
