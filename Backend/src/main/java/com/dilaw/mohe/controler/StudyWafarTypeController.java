package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.StudyWafarTypeRequest;
import com.dilaw.mohe.model.StudyWafarType;
import com.dilaw.mohe.service.StudyWafarTypeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/studyWafarType")
@PreAuthorize(MoheSecurityExpressions.WAFAR)
public class StudyWafarTypeController {

    @Autowired
    private StudyWafarTypeService service;

    /**
     * The wafar catalogue, as a reference list.
     *
     * <p>A finance screen has to be able to say *which* wafar a student's money
     * is held against — the finance recipients form names it beside the amount
     * it refuses to hand over. Naming a type is not seeing wafar records, so the
     * list is read under the lookup gate rather than costing the caller the
     * {@code WAFAR} authority. Everything else on this controller keeps it.
     */
    @PreAuthorize(MoheSecurityExpressions.LOOKUP_TABLE)
    @GetMapping("/")
    public ResponseEntity<List<StudyWafarType>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudyWafarType> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping("/")
    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    public ResponseEntity<StudyWafarType> create(@Valid @RequestBody StudyWafarTypeRequest request) {
        return ResponseEntity.ok(service.createFromRequest(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    public ResponseEntity<StudyWafarType> update(@PathVariable Integer id, @Valid @RequestBody StudyWafarTypeRequest request) {
        return ResponseEntity.ok(service.updateFromRequest(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
