package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.*;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.service.StudentAccessGuard;
import com.dilaw.mohe.service.StudentDataService;
import com.dilaw.mohe.service.StudentDeathService;
import com.dilaw.mohe.service.StudentFamilyService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.security.policy.AccessControl;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("api/v1/studentdata")
@PreAuthorize(MoheSecurityExpressions.STUDENT_PII)
public class StudentFamilyController {

    @Autowired
    private AccessControl accessControl;

    @Autowired
    private StudentFamilyService studentFamilyService;

    @Autowired
    private StudentDeathService studentDeathService;

    @Autowired
    private StudentDataService studentDataService;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private StudentAccessGuard studentAccessGuard;

    /*
     * Every endpoint below is keyed on a student id, so each one resolves the
     * student through the guard first. Parents and relatives name real people —
     * a name, a relationship, a contact — and had no country check at all.
     */

    @GetMapping("/{id}/parents")
    public List<StudentParentDto> getParents(@PathVariable Integer id) {
        studentAccessGuard.requireStudent(id);
        return studentFamilyService.getParents(id);
    }

    @PutMapping("/{id}/parents")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public List<StudentParentDto> replaceParents(@PathVariable Integer id, @RequestBody List<StudentParentDto> body) {
        studentAccessGuard.requireStudent(id);
        return studentFamilyService.replaceParents(id, body);
    }

    @PostMapping("/{id}/parents")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public StudentParentDto createParent(@PathVariable Integer id, @Valid @RequestBody StudentParentDto body) {
        studentAccessGuard.requireStudent(id);
        return studentFamilyService.createParent(id, body);
    }

    @PutMapping("/{id}/parents/{parentId}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public StudentParentDto updateParent(
            @PathVariable Integer id, @PathVariable Integer parentId, @Valid @RequestBody StudentParentDto body) {
        studentAccessGuard.requireStudent(id);
        return studentFamilyService.updateParent(id, parentId, body);
    }

    @DeleteMapping("/{id}/parents/{parentId}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<Void> deleteParent(@PathVariable Integer id, @PathVariable Integer parentId) {
        studentAccessGuard.requireStudent(id);
        studentFamilyService.deleteParent(id, parentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/relatives")
    public List<StudentRelativeDto> getRelatives(@PathVariable Integer id) {
        studentAccessGuard.requireStudent(id);
        return studentFamilyService.getRelatives(id);
    }

    @PutMapping("/{id}/relatives")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public List<StudentRelativeDto> replaceRelatives(@PathVariable Integer id, @RequestBody List<StudentRelativeDto> body) {
        studentAccessGuard.requireStudent(id);
        return studentFamilyService.replaceRelatives(id, body);
    }

    @PostMapping("/{id}/relatives")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public StudentRelativeDto createRelative(@PathVariable Integer id, @Valid @RequestBody StudentRelativeDto body) {
        studentAccessGuard.requireStudent(id);
        return studentFamilyService.createRelative(id, body);
    }

    @PutMapping("/{id}/relatives/{relativeId}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public StudentRelativeDto updateRelative(
            @PathVariable Integer id, @PathVariable Integer relativeId, @Valid @RequestBody StudentRelativeDto body) {
        studentAccessGuard.requireStudent(id);
        return studentFamilyService.updateRelative(id, relativeId, body);
    }

    @DeleteMapping("/{id}/relatives/{relativeId}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<Void> deleteRelative(@PathVariable Integer id, @PathVariable Integer relativeId) {
        studentAccessGuard.requireStudent(id);
        studentFamilyService.deleteRelative(id, relativeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/relatives/search")
    public List<StudentRelativeSearchDto> searchRelatives(
            @RequestParam("q") String q,
            @RequestParam(value = "excludeStudentId", required = false) Integer excludeStudentId) {
        return studentFamilyService.searchRelatives(q, excludeStudentId);
    }

    @GetMapping("/{id}/death-case")
    public ResponseEntity<StudentDeathCaseDto> getDeathCase(@PathVariable Integer id) {
        studentAccessGuard.requireStudent(id);
        Optional<StudentDeathCaseDto> dto = studentDeathService.getDeathCase(id);
        return dto.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/death-case")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public StudentDeathCaseDto createDeathCase(@PathVariable Integer id, @Valid @RequestBody StudentDeathCaseRequest body) {
        StudentDatum existing = studentDataService.getStudentData(id);
        validateStudyCountryScope(existing);
        return studentDeathService.createDeathCase(id, body);
    }

    @PutMapping("/{id}/death-case/close")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public StudentDeathCaseDto closeDeathCase(@PathVariable Integer id) {
        StudentDatum existing = studentDataService.getStudentData(id);
        validateStudyCountryScope(existing);
        return studentDeathService.closeDeathCase(id);
    }

    @PutMapping("/{id}/death-case/steps/{stepId}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public StudentDeathProcedureStepDto updateDeathProcedureStep(
            @PathVariable Integer id,
            @PathVariable Integer stepId,
            @Valid @RequestBody StudentDeathProcedureStepUpdateRequest body) {
        StudentDatum existing = studentDataService.getStudentData(id);
        validateStudyCountryScope(existing);
        return studentDeathService.updateProcedureStep(id, stepId, body);
    }

    /**
     * UMS scope is tied to STUDY_COUNTRY_IN (study destination), not nationality.
     *
     * <p>A student with no country on record is ministry-only; guarding with
     * {@code country != null} made such a record readable by every officer.
     */
    private void validateStudyCountryScope(StudentDatum student) {
        if (student == null) return;
        // R-16/R-17 (6 Sep 2026): the rule is StudentDatumPolicy — the study country
        // must be in the caller's scope, and a student with no country is ministry-only.
        accessControl.assertAllowed(student, "EDIT");
    }
}
