package com.dilaw.mohe.systemsettings.controller;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.systemsettings.dto.SysSettingsDto;
import com.dilaw.mohe.systemsettings.service.SysSettingsService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("api/v1/sys-settings")
public class SysSettingsController {

    private final SysSettingsService service;

    public SysSettingsController(SysSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<SysSettingsDto> getSettings() {
        return ResponseEntity.ok(service.getSettings());
    }

    @PutMapping
    @PreAuthorize(MoheSecurityExpressions.SUPER_ADMIN)
    public ResponseEntity<SysSettingsDto> updateSettings(@Valid @RequestBody SysSettingsDto dto) {
        return ResponseEntity.ok(service.updateSettings(dto));
    }

    @PostMapping("/logo")
    @PreAuthorize(MoheSecurityExpressions.SUPER_ADMIN)
    public ResponseEntity<SysSettingsDto> uploadLogo(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(service.uploadLogo(file));
    }

    /**
     * The logo, anonymous (the login page shows it).
     *
     * <p>Two headers on top of the content type, because the file may be an SVG
     * and an SVG is a document: {@code Content-Security-Policy: sandbox} forbids
     * script, forms and plugins in it and gives it a null origin should a browser
     * ever open it directly, and {@code X-Content-Type-Options: nosniff} stops a
     * browser second-guessing the type. The upload already refuses scripted SVG
     * ({@code FileUploadValidator}); these are the second layer for a file that
     * predates that check (revision item 1.11).
     */
    @GetMapping("/logo")
    public ResponseEntity<byte[]> getLogo() {
        byte[] bytes = service.getLogoBytes();
        String contentType = service.getLogoContentType();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setCacheControl("max-age=86400");
        headers.set("Content-Security-Policy", "sandbox");
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
