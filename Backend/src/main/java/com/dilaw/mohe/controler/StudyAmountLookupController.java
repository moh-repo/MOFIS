package com.dilaw.mohe.controler;

import com.dilaw.mohe.model.StudyAmountLookup;
import com.dilaw.mohe.service.StudyAmountLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.dilaw.mohe.config.MoheSecurityExpressions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Reads take STUDENT_PII because Add Student resolves a student's amount here. The
 * maintenance page opens with SETTINGS, so a settings viewer opened it to "Access
 * denied" (live QA R5-08): reads also take the settings read gate. The rules are
 * reference data (amounts per degree and agency type), still country-scoped.
 */
@PreAuthorize(MoheSecurityExpressions.STUDENT_PII + " or " + MoheSecurityExpressions.SYSTEM_ADMIN)
@RestController
@RequestMapping("/api/v1/lookup")
public class StudyAmountLookupController {

    @Autowired
    private StudyAmountLookupService service;

    /**
     * The amount in force for a degree and agency type, resolved for one attaché.
     *
     * <p>{@code countryId} is the <b>student's</b> study country, not the caller's;
     * see {@link StudyYearsLookupController#getStudyYears} for the full reasoning.
     */
    @GetMapping("/study-amount")
    public ResponseEntity<?> getStudyAmount(
            @RequestParam(required = false) Integer degreeId,
            @RequestParam(required = false) Integer agencyTypeId,
            @RequestParam(required = false) Integer countryId) {
        if (degreeId != null && agencyTypeId != null) {
            Integer effective = countryId != null ? countryId : service.defaultCountryForRead();
            Optional<StudyAmountLookup> found = service.resolve(degreeId, agencyTypeId, effective);
            // Not Map.of: it throws on a null value, so "no rule" (or a rule with no
            // amount) was a 500 instead of an empty answer (live QA #6).
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("amount", found.map(StudyAmountLookup::getAmount).orElse(null));
            found.ifPresent(entry -> body.put("currencyId",
                    entry.getCurrencyId() != null ? entry.getCurrencyId() : 0));
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/study-amount/all")
    public ResponseEntity<List<StudyAmountLookup>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/study-amount/{id}")
    public ResponseEntity<StudyAmountLookup> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @PostMapping("/study-amount")
    public ResponseEntity<StudyAmountLookup> create(@Valid @RequestBody StudyAmountLookup entity) {
        return ResponseEntity.ok(service.create(entity));
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @PutMapping("/study-amount/{id}")
    public ResponseEntity<StudyAmountLookup> update(@PathVariable Integer id, @Valid @RequestBody StudyAmountLookup entity) {
        return ResponseEntity.ok(service.update(id, entity));
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @DeleteMapping("/study-amount/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }
}
