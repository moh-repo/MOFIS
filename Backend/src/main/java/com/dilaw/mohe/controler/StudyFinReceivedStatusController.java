package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.StudyFinReceivedStatus;
import com.dilaw.mohe.service.StudyFinReceivedStatusService;
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
@RequestMapping("api/v1/studyFinReceivedStatus")
@PreAuthorize(MoheSecurityExpressions.FINANCE)
public class StudyFinReceivedStatusController {

    @Autowired
    private StudyFinReceivedStatusService service;

    @GetMapping("/")
    public List<StudyFinReceivedStatus> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public StudyFinReceivedStatus getById(@PathVariable Integer id) {
        return service.getById(id);
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @PostMapping("/")
    public StudyFinReceivedStatus create(@RequestBody StudyFinReceivedStatus entity) {
        return service.create(entity);
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @PutMapping("/{id}")
    public StudyFinReceivedStatus update(@PathVariable Integer id, @RequestBody StudyFinReceivedStatus entity) {
        return service.update(id, entity);
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        service.delete(id);
    }
}
