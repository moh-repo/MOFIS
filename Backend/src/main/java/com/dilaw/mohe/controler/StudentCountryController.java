package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.StudentCountry;
import com.dilaw.mohe.service.StudentCountryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
 * Lookup maintenance (plan item 1.6): the class-level gate admits the page's
 * VIEW code, which is right for reading reference data and wrong for editing
 * it. Writes carry SETTINGS_WRITE — the same authority LookupTableController
 * requires — so a reader of the student or finance screens cannot rename a
 * country or a bank. A method-level annotation replaces the class-level one.
 */
@RestController
@RequestMapping("api/v1/studentCountry")
@PreAuthorize(MoheSecurityExpressions.STUDENT_PII)
public class StudentCountryController {

    @Autowired
    private StudentCountryService service;

    @GetMapping("/")
    public List<StudentCountry> getAllStudentCountry() {
        return service.getStudentCountriesAll();
    }

    @GetMapping("/{id}")
    public StudentCountry getStudentCountryById(@PathVariable Integer id) {
        return service.getStudentCountryById(id);
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @PostMapping("/")
    public StudentCountry createStudentCountry(@RequestBody StudentCountry entity) {
        return service.createStudentCountry(entity);
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @PutMapping("/{id}")
    public StudentCountry updateStudentCountry(@PathVariable Integer id, @RequestBody StudentCountry entity) {
        return service.updateStudentCountry(id, entity);
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @DeleteMapping("/{id}")
    public void deleteRole(@PathVariable Integer id) {
        service.deleteStudentCountry(id);
    }
}
