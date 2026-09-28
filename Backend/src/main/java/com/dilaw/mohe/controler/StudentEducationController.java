package com.dilaw.mohe.controler;

import com.dilaw.mohe.model.StudentEduction;
import com.dilaw.mohe.service.StudentEductionService;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.dilaw.mohe.config.MoheSecurityExpressions;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import com.dilaw.mohe.config.MoheSecurityExpressions;

/*
 * Lookup maintenance (plan item 1.6): the class-level gate admits the page's
 * VIEW code, which is right for reading reference data and wrong for editing
 * it. Writes carry SETTINGS_WRITE — the same authority LookupTableController
 * requires — so a reader of the student or finance screens cannot rename a
 * country or a bank. A method-level annotation replaces the class-level one.
 */
@PreAuthorize(MoheSecurityExpressions.STUDENT_PII)
@RestController
@RequestMapping("api/v1/studentEduction")
public class StudentEducationController {

    @Autowired
    private StudentEductionService service;

    @GetMapping("/")
    public List<StudentEduction> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public StudentEduction getById(@PathVariable Integer id) {
        return service.getById(id);
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @PostMapping("/")
    public StudentEduction create(@Valid @RequestBody StudentEduction entity) {
        return service.create(entity);
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @PutMapping("/{id}")
    public StudentEduction update(@PathVariable Integer id, @Valid @RequestBody StudentEduction entity) {
        return service.update(id, entity);
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        service.delete(id);
    }
}
