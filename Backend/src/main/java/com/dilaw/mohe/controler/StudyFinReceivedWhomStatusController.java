package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.StudyFinReceivedWhomStatus;
import com.dilaw.mohe.service.StudyFinReceivedWhomStatusService;
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
@RequestMapping("api/v1/studyFinReceivedWhomStatus")
@PreAuthorize(MoheSecurityExpressions.FINANCE)
public class StudyFinReceivedWhomStatusController {

    @Autowired
    private StudyFinReceivedWhomStatusService service;

    @GetMapping("/")
    public List<StudyFinReceivedWhomStatus> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public StudyFinReceivedWhomStatus getById(@PathVariable Integer id) {
        return service.getById(id);
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @PostMapping("/")
    public StudyFinReceivedWhomStatus create(@RequestBody StudyFinReceivedWhomStatus entity) {
        return service.create(entity);
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @PutMapping("/{id}")
    public StudyFinReceivedWhomStatus update(@PathVariable Integer id, @RequestBody StudyFinReceivedWhomStatus entity) {
        return service.update(id, entity);
    }

    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        service.delete(id);
    }
}
