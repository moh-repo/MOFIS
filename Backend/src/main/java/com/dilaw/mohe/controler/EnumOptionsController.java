package com.dilaw.mohe.controler;

import com.dilaw.mohe.dto.EnumOptionDto;
import com.dilaw.mohe.service.EnumOptionsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/v1/enums")
public class EnumOptionsController {

    @Autowired
    private EnumOptionsService enumOptionsService;

    @GetMapping
    public ResponseEntity<List<String>> listAvailable() {
        return ResponseEntity.ok(enumOptionsService.listAvailable().orElse(List.of()));
    }

    @GetMapping("/{name}")
    public ResponseEntity<List<EnumOptionDto>> getOptions(
            @PathVariable String name,
            @RequestParam(defaultValue = "ar") String lang) {
        List<EnumOptionDto> options = enumOptionsService.getOptions(name, lang);
        if (options.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(options);
    }
}
