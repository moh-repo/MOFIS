package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.StudentTotalReportAll;
import com.dilaw.mohe.service.StudentTotalReportAllService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/studentTotalReportAll")
@PreAuthorize(MoheSecurityExpressions.REPORT_ACADEMIC)
public class StudentTotalReportAllController {

    @Autowired
    private StudentTotalReportAllService service;

    @GetMapping("/")
    public List<StudentTotalReportAll> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public StudentTotalReportAll getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping("/")
    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    public StudentTotalReportAll create(@RequestBody StudentTotalReportAll entity) {
        return service.create(entity);
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    public StudentTotalReportAll update(@PathVariable String id, @RequestBody StudentTotalReportAll entity) {
        return service.update(id, entity);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}