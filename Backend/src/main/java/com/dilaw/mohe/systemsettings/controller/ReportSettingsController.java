package com.dilaw.mohe.systemsettings.controller;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.systemsettings.dto.ReportTypeSettingsDto;
import com.dilaw.mohe.systemsettings.service.ReportSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/report-settings")
public class ReportSettingsController {

    private final ReportSettingsService service;

    public ReportSettingsController(ReportSettingsService service) {
        this.service = service;
    }

    // Read by every reports screen; any signed-in user. Stated explicitly (O-04)
    // rather than inherited from the chain's .authenticated() so the intent is
    // visible here and the architecture test can tell "any user" from "forgot".
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<List<ReportTypeSettingsDto>> getAll() {
        return ResponseEntity.ok(service.getAllReportTypes());
    }

    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    @GetMapping("/{code}")
    public ResponseEntity<ReportTypeSettingsDto> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(service.getByCode(code));
    }

    /** The report codes without settings yet, for "Add type" to pick from (DEC-22). */
    @GetMapping("/catalogue")
    @PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
    public ResponseEntity<List<String>> catalogue() {
        return ResponseEntity.ok(service.unconfiguredCodes());
    }

    @PostMapping
    @PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
    public ResponseEntity<ReportTypeSettingsDto> create(@Valid @RequestBody ReportTypeSettingsDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
    public ResponseEntity<ReportTypeSettingsDto> update(
            @PathVariable Integer id,
            @Valid @RequestBody ReportTypeSettingsDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
