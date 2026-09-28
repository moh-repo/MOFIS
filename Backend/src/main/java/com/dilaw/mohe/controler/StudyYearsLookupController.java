package com.dilaw.mohe.controler;

import com.dilaw.mohe.model.StudyYearsLookup;
import com.dilaw.mohe.service.StudyYearsLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.dilaw.mohe.config.MoheSecurityExpressions;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import com.dilaw.mohe.config.MoheSecurityExpressions;

/*
 * Lookup maintenance (plan item 1.6): the class-level gate admits the page's
 * VIEW code, which is right for reading reference data and wrong for editing
 * it. Writes carry SETTINGS_WRITE — the same authority LookupTableController
 * requires — so a reader of the student or finance screens cannot rename a
 * country or a bank. A method-level annotation replaces the class-level one.
 *
 * Reads take STUDENT_PII because Add Student resolves a student's years here. The
 * maintenance page opens with SETTINGS, so a settings viewer opened it to "Access
 * denied" (live QA R5-08): reads also take the settings read gate. The rules are
 * reference data (years per degree and major), still country-scoped.
 */
@PreAuthorize(MoheSecurityExpressions.STUDENT_PII + " or " + MoheSecurityExpressions.SYSTEM_ADMIN)
@RestController
@RequestMapping("/api/v1/lookup")
public class StudyYearsLookupController {

    @Autowired
    private StudyYearsLookupService service;

    /**
     * The years in force for a degree and major, resolved for one attaché.
     *
     * <p>{@code countryId} is the <b>student's</b> study country, not the caller's.
     * Student-add sends it so a Jordanian student gets Jordan's rule even when a
     * ministry user is entering them. Omitted, it falls back to the caller's own
     * attaché when they serve exactly one, and to the shared default otherwise —
     * so existing clients that never sent it keep the behaviour they had.
     */
    @GetMapping("/study-years")
    public ResponseEntity<?> getStudyYears(
            @RequestParam(required = false) Integer degreeId,
            @RequestParam(required = false) Integer majorId,
            @RequestParam(required = false) Integer countryId) {
        if (degreeId != null && majorId != null) {
            Integer effective = countryId != null ? countryId : service.defaultCountryForRead();
            Optional<StudyYearsLookup> found = service.resolve(degreeId, majorId, effective);
            // Not Map.of: it throws on a null value, which made "no rule for this
            // degree and major" a 500 and broke the Add Student auto-fill (live QA #6).
            return ResponseEntity.ok(Collections.singletonMap("studyYears",
                    found.map(StudyYearsLookup::getStudyYears).orElse(null)));
        }
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/study-years/all")
    public ResponseEntity<List<StudyYearsLookup>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/study-years/{id}")
    public ResponseEntity<StudyYearsLookup> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @PostMapping("/study-years")
    public ResponseEntity<StudyYearsLookup> create(@Valid @RequestBody StudyYearsLookup entity) {
        return ResponseEntity.ok(service.create(entity));
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @PutMapping("/study-years/{id}")
    public ResponseEntity<StudyYearsLookup> update(@PathVariable Integer id, @Valid @RequestBody StudyYearsLookup entity) {
        return ResponseEntity.ok(service.update(id, entity));
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @DeleteMapping("/study-years/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }
}
