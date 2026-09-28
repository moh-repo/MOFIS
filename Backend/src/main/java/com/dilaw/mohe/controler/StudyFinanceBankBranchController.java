package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.StudyFinanceBankBranch;
import com.dilaw.mohe.service.StudyFinanceBankBranchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
 * Lookup maintenance (plan item 1.6): the class-level gate admits the page's
 * VIEW code, which is right for reading reference data and wrong for editing
 * it. Writes carry SETTINGS_WRITE — the same authority LookupTableController
 * requires — so a reader of the student or finance screens cannot rename a
 * country or a bank. A method-level annotation replaces the class-level one.
 *
 * The maintenance page opens with SETTINGS, and its list read needed FINANCE, so a
 * settings viewer opened it to "Access denied" (live QA R5-08): reads also take the
 * settings read gate. The rows stay country-scoped; branch names are reference data.
 */
@RestController
@RequestMapping("api/v1/studyFinanceBankBranch")
@PreAuthorize(MoheSecurityExpressions.FINANCE + " or " + MoheSecurityExpressions.SYSTEM_ADMIN)
public class StudyFinanceBankBranchController {

    @Autowired
    private StudyFinanceBankBranchService service;

    @GetMapping("/")
    public ResponseEntity<List<StudyFinanceBankBranch>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudyFinanceBankBranch> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PreAuthorize(MoheSecurityExpressions.COUNTRY_CONFIG_WRITE)
    @PostMapping("/")
    public ResponseEntity<StudyFinanceBankBranch> create(@RequestBody StudyFinanceBankBranch entity) {
        return ResponseEntity.ok(service.create(entity));
    }

    @PreAuthorize(MoheSecurityExpressions.COUNTRY_CONFIG_WRITE)
    @PutMapping("/{id}")
    public ResponseEntity<StudyFinanceBankBranch> update(@PathVariable Integer id, @RequestBody StudyFinanceBankBranch entity) {
        return ResponseEntity.ok(service.update(id, entity));
    }

    @PreAuthorize(MoheSecurityExpressions.COUNTRY_CONFIG_WRITE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
