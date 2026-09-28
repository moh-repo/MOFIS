package com.dilaw.mohe.alerteventcases.service;

import com.dilaw.mohe.codes.AlertCaseStatus;

import com.dilaw.mohe.alerteventcases.dto.AlertEventScenarioDto;
import com.dilaw.mohe.alerteventcases.dto.AlertEventScenarioRequest;
import com.dilaw.mohe.alerteventcases.model.AlertEventScenario;
import com.dilaw.mohe.alerteventcases.repository.AlertEventCaseRepository;
import com.dilaw.mohe.alerteventcases.repository.AlertEventScenarioRepository;
import com.dilaw.mohe.alerteventcases.repository.spec.AlertEventCaseSpec;
import com.dilaw.mohe.service.BusinessLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AlertEventScenarioService {

    @Autowired
    private AlertEventScenarioRepository scenarioRepo;

    @Autowired
    private AlertEventCaseRepository caseRepo;

    @Autowired
    private BusinessLookupService businessLookupService;

    public List<AlertEventScenarioDto> getAll() {
        return scenarioRepo.findAll().stream()
                .map(AlertEventScenarioDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public AlertEventScenarioDto create(AlertEventScenarioRequest req) {
        validateRequest(req, null);
        if (scenarioRepo.findByScenarioCode(req.getScenarioCode()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Scenario code already exists: " + req.getScenarioCode());
        }
        AlertEventScenario s = new AlertEventScenario();
        applyRequest(s, req);
        s.setCreatedAt(Instant.now());
        return AlertEventScenarioDto.from(scenarioRepo.save(s));
    }

    @Transactional
    public AlertEventScenarioDto save(Integer id, AlertEventScenarioRequest req) {
        AlertEventScenario s = scenarioRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scenario not found: " + id));
        validateRequest(req, id);
        // If code is changing, check uniqueness
        if (req.getScenarioCode() != null && !req.getScenarioCode().equals(s.getScenarioCode())) {
            scenarioRepo.findByScenarioCode(req.getScenarioCode()).ifPresent(existing -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Scenario code already exists: " + req.getScenarioCode());
            });
        }
        applyRequest(s, req);
        s.setUpdatedAt(Instant.now());
        return AlertEventScenarioDto.from(scenarioRepo.save(s));
    }

    @Transactional
    public AlertEventScenarioDto patch(Integer id, Boolean isActive, Integer thresholdDays) {
        AlertEventScenario s = scenarioRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scenario not found: " + id));
        if (isActive != null) s.setIsActive(isActive);
        if (thresholdDays != null) s.setThresholdDays(thresholdDays);
        s.setUpdatedAt(Instant.now());
        return AlertEventScenarioDto.from(scenarioRepo.save(s));
    }

    @Transactional
    public void delete(Integer id) {
        AlertEventScenario s = scenarioRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scenario not found: " + id));
        long activeCases = caseRepo.count(
                AlertEventCaseSpec.notDeleted()
                        .and(AlertEventCaseSpec.byScenarioCode(s.getScenarioCode()))
                        .and((root, query, cb) -> root.get("status").in(List.of(AlertCaseStatus.OPEN, AlertCaseStatus.ACKNOWLEDGED)))
        );
        if (activeCases > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete scenario with " + activeCases + " open/acknowledged case(s). Resolve or dismiss them first.");
        }
        scenarioRepo.delete(s);
    }

    private void validateRequest(AlertEventScenarioRequest req, Integer existingId) {
        if (req.getScenarioCode() == null || req.getScenarioCode().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scenario code is required");
        if (req.getScenarioNameEn() == null || req.getScenarioNameEn().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "English name is required");
        if (req.getScenarioNameAr() == null || req.getScenarioNameAr().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arabic name is required");
        if (req.getSeverity() != null && !businessLookupService.isValidAlertSeverity(req.getSeverity()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid severity value");
    }

    private void applyRequest(AlertEventScenario s, AlertEventScenarioRequest req) {
        if (req.getScenarioCode() != null) s.setScenarioCode(req.getScenarioCode().toUpperCase().trim());
        if (req.getScenarioNameEn() != null) s.setScenarioNameEn(req.getScenarioNameEn().trim());
        if (req.getScenarioNameAr() != null) s.setScenarioNameAr(req.getScenarioNameAr().trim());
        if (req.getDescriptionEn() != null) s.setDescriptionEn(req.getDescriptionEn().trim());
        if (req.getDescriptionAr() != null) s.setDescriptionAr(req.getDescriptionAr().trim());
        if (req.getSeverity() != null) s.setSeverity(req.getSeverity().toUpperCase());
        s.setThresholdDays(req.getThresholdDays());
        s.setIsActive(req.getIsActive() != null ? req.getIsActive() : true);
    }
}
