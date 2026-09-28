package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.ExtensionTypeDto;
import com.dilaw.mohe.dto.ExtensionTypeRequest;
import com.dilaw.mohe.service.ExtensionTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/extension-types")
@PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
@RequiredArgsConstructor
public class ExtensionTypeController {

    private final ExtensionTypeService service;

    @GetMapping("/")
    public ResponseEntity<List<ExtensionTypeDto>> getAllExtensionTypes(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        List<ExtensionTypeDto> result = activeOnly
                ? service.getAllActiveExtensionTypes()
                : service.getAllExtensionTypes();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExtensionTypeDto> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getExtensionTypeById(id));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ExtensionTypeDto> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(service.getExtensionTypeByCode(code));
    }

    @PostMapping("/")
    public ResponseEntity<ExtensionTypeDto> create(@RequestBody ExtensionTypeRequest request) {
        return ResponseEntity.ok(service.createExtensionType(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExtensionTypeDto> update(@PathVariable Integer id, @RequestBody ExtensionTypeRequest request) {
        return ResponseEntity.ok(service.updateExtensionType(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.deleteExtensionType(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/toggle-active")
    public ResponseEntity<ExtensionTypeDto> toggleActive(@PathVariable Integer id) {
        return ResponseEntity.ok(service.toggleActive(id));
    }
}