package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.StudentDataArch;
import com.dilaw.mohe.service.StudentDataArchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/studentDataArch")
@PreAuthorize(MoheSecurityExpressions.STUDENT_ARCHIVE_READ)
public class StudentDataArchController {

    @Autowired
    private StudentDataArchService service;

    @GetMapping("/")
    public List<StudentDataArch> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public StudentDataArch getById(@PathVariable Integer id) {
        return service.getById(id);
    }

    /*
     * The archive is the system's history, written by the quarter close and read by
     * Student History and the finance history (DEC-08 a). The generic create, the
     * whole-row update and the delete this controller used to expose were reachable
     * through the API by anyone holding ARCHIVING.EDIT / ARCHIVING.DELETE although no
     * screen offered them (live QA H1, 23 Sep 2026); they are gone. The one write that
     * remains is the finance-column correction below, and StudentArchiveEndpointsTest
     * holds that this stays the only one.
     */

    /**
     * The finance history page's save (review F5): only the finance columns of the
     * archived row change, whatever else the body carries. The student's identity in
     * an archived quarter is a record of what was on file then, not something the
     * finance page corrects.
     */
    @PutMapping("/{id}/finance")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_ARCH_WRITE)
    public StudentDataArch updateFinance(@PathVariable Integer id, @RequestBody StudentDataArch entity) {
        return service.updateFinanceColumns(id, entity);
    }

    @GetMapping("/paginated")
    public Page<StudentDataArch> getAllPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        return service.findAllWithPagination(pageable);
    }

    @GetMapping("/search")
    public List<StudentDataArch> searchByName(@RequestParam String name) {
        return service.findByStudentNameContaining(name);
    }

    /** Sortable columns exposed to the client, mapped to their entity property names. */
    private static final java.util.Set<String> SORTABLE = java.util.Set.of(
            "id", "studentId", "studentName", "studentMajorName", "studentAddedQuarter",
            "studentAmount", "archiveDate", "insertedToArchDate", "studentGender", "studentPassportNumber",
            // Student History's snapshot-quarter column (live QA STD-004).
            "currentQuarter");

    /**
     * Unknown fields used to fall back silently to {@code id desc}; since plan
     * item 2.4 they are a 400 through the shared {@link com.dilaw.mohe.util.SortWhitelist},
     * so a mistyped column is reported rather than quietly ignored. No field at all
     * still means {@code id desc}.
     */
    private Sort resolveSort(String sortField, String sortDir) {
        if (sortField == null || sortField.isBlank()) {
            return Sort.by("id").descending();
        }
        return com.dilaw.mohe.util.SortWhitelist.sort(sortField, sortDir, SORTABLE);
    }

    @GetMapping({"/distinct-paginated", "/distinct/paginated"})
    public Page<StudentDataArch> getDistinctStudentsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer filterStudentId,
            @RequestParam(required = false) String filterName,
            @RequestParam(required = false) String filterPassport,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDir) {
        Pageable pageable = PageRequest.of(page, size, resolveSort(sortField, sortDir));
        if (filterStudentId != null || filterName != null || filterPassport != null) {
            return service.findDistinctStudentsWithFieldFilters(filterStudentId, filterName, filterPassport, pageable);
        }
        return service.findDistinctStudentsWithLatestData(pageable);
    }

    @GetMapping({"/distinct-search", "/distinct/search"})
    public List<StudentDataArch> searchDistinctStudentsByName(@RequestParam String name) {
        return service.findDistinctStudentsByNameWithLatestData(name);
    }

    @GetMapping({"/distinct-all", "/distinct/all"})
    public List<StudentDataArch> getAllDistinctStudents() {
        return service.findAllDistinctStudentsWithLatestData();
    }

    @GetMapping("/financial-records/{name}")
    public List<StudentDataArch> getFinancialRecordsByStudentName(@PathVariable String name) {
        return service.findAllFinancialRecordsByStudentName(name);
    }

    /** Preferred over the name-based lookup: gathers a student's snapshots by real STUDENT_ID. */
    @GetMapping("/financial-records-by-id/{studentId}")
    public List<StudentDataArch> getFinancialRecordsByStudentId(@PathVariable Integer studentId) {
        return service.findAllFinancialRecordsByStudentId(studentId);
    }
}