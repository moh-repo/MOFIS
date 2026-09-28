package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.StudentQuickEditDto;
import com.dilaw.mohe.dto.StudentQuickEditMetadataDto;
import com.dilaw.mohe.service.StudentQuickEditService;
import com.dilaw.mohe.service.StudentQuickEditMetadataService;
import com.dilaw.mohe.util.SortWhitelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("api/v1/studentQuickEdit")
@PreAuthorize(MoheSecurityExpressions.STUDENT_PII)
public class StudentQuickEditController {

    /**
     * The paged list is a page of {@code StudentDatum} (the DTO is mapped from it),
     * so these are {@code StudentDatum} properties the screen shows (plan item 2.4).
     */
    static final Set<String> SORTABLE = Set.of(
            "studentId", "studentName", "studentPassportNumber", "studentGender", "studentAmount",
            "studentAddedQuarter", "studyCountryIn", "studyUniversityIn", "studyMajorIn",
            "studentMidate", "studentEmail");

    @Autowired
    private StudentQuickEditService service;

    @Autowired
    private StudentQuickEditMetadataService metadataService;

    @GetMapping({"", "/"})
    public ResponseEntity<List<StudentQuickEditDto>> getAll() {
        return ResponseEntity.ok(service.getAllCombinedData());
    }

    @GetMapping("/paged")
    public ResponseEntity<Page<StudentQuickEditDto>> getAllPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "studentId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        
        Sort sort = SortWhitelist.sort(sortBy, sortDir, SORTABLE);
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(service.getAllCombinedData(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentQuickEditDto> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/metadata")
    public ResponseEntity<StudentQuickEditMetadataDto> getMetadata() {
        return ResponseEntity.ok(metadataService.getMetadata());
    }

    /**
     * Quick edits go through the approval workflow: the response carries a status
     * (PENDING / NO_CHANGES) rather than the saved row, since changes apply only once approved.
     */
    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Integer id,
            @Valid @RequestBody StudentQuickEditDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }
}

