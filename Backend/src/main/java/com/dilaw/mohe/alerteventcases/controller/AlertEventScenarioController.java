package com.dilaw.mohe.alerteventcases.controller;

import com.dilaw.mohe.alerteventcases.dto.AlertEventScenarioDto;
import com.dilaw.mohe.alerteventcases.dto.AlertEventScenarioRequest;
import com.dilaw.mohe.alerteventcases.service.AlertEventScenarioService;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alert-scenarios")
public class AlertEventScenarioController {

    @Autowired
    private AlertEventScenarioService scenarioService;

    @GetMapping
    @PreAuthorize(MoheSecurityExpressions.ALERT_EVENTS_VIEW)
    public List<AlertEventScenarioDto> getAll() {
        return scenarioService.getAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MoheSecurityExpressions.ALERT_SCENARIOS_MANAGE)
    public AlertEventScenarioDto create(@RequestBody AlertEventScenarioRequest req) {
        return scenarioService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.ALERT_SCENARIOS_MANAGE)
    public AlertEventScenarioDto save(@PathVariable Integer id,
                                      @RequestBody AlertEventScenarioRequest req) {
        return scenarioService.save(id, req);
    }

    @PatchMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.ALERT_SCENARIOS_MANAGE)
    public AlertEventScenarioDto patch(@PathVariable Integer id,
                                       @RequestBody Map<String, Object> body) {
        Boolean isActive = body.containsKey("isActive") ? (Boolean) body.get("isActive") : null;
        Integer thresholdDays = body.containsKey("thresholdDays")
                ? (body.get("thresholdDays") instanceof Number n ? n.intValue() : null)
                : null;
        return scenarioService.patch(id, isActive, thresholdDays);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MoheSecurityExpressions.ALERT_SCENARIOS_MANAGE)
    public void delete(@PathVariable Integer id) {
        scenarioService.delete(id);
    }
}
