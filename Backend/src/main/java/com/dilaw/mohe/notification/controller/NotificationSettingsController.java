package com.dilaw.mohe.notification.controller;

import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.notification.dto.NotificationSettingsDto;
import com.dilaw.mohe.notification.dto.NotificationTestRequest;
import com.dilaw.mohe.notification.dto.NotificationTestResult;
import com.dilaw.mohe.notification.service.NotificationSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/notification-settings")
public class NotificationSettingsController {

    private final NotificationSettingsService service;

    public NotificationSettingsController(NotificationSettingsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATIONS.SETTINGS')")
    public ResponseEntity<NotificationSettingsDto> getSettings(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getSettings(countryId));
    }

    /** The global row and every attaché row the caller may see, secrets masked. */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('NOTIFICATIONS.SETTINGS')")
    public ResponseEntity<List<NotificationSettingsDto>> list() {
        return ResponseEntity.ok(service.list());
    }

    /** Adds an attaché's own row (409 when it already has one). */
    @PostMapping
    @PreAuthorize("hasAuthority('NOTIFICATIONS.SETTINGS')")
    public ResponseEntity<NotificationSettingsDto> create(@Valid @RequestBody NotificationSettingsDto dto) {
        int userId = (int) SecurityRequestUtils.currentUserIdOrDefault(1L);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto, userId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('NOTIFICATIONS.SETTINGS')")
    public ResponseEntity<NotificationSettingsDto> updateSettings(@Valid @RequestBody NotificationSettingsDto dto) {
        int userId = (int) SecurityRequestUtils.currentUserIdOrDefault(1L);
        return ResponseEntity.ok(service.updateSettings(dto, userId));
    }

    /** Removes an attaché's own row; its sends fall back to the global settings. */
    @DeleteMapping("/{countryId}")
    @PreAuthorize("hasAuthority('NOTIFICATIONS.SETTINGS')")
    public ResponseEntity<Void> delete(@PathVariable Integer countryId) {
        service.delete(countryId);
        return ResponseEntity.noContent().build();
    }

    /** Sends one test email, SMS or WhatsApp message through the saved settings (review R7). */
    @PostMapping("/test")
    @PreAuthorize("hasAuthority('NOTIFICATIONS.SETTINGS')")
    public ResponseEntity<NotificationTestResult> test(@RequestBody NotificationTestRequest req) {
        return ResponseEntity.ok(service.testSend(req));
    }
}
