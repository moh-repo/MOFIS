package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.StudyUniversityFeeArch;
import com.dilaw.mohe.service.StudyUniversityFeeArchService;
import com.dilaw.mohe.util.SortWhitelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * Archived university-fee rows. Reads under {@code FINANCE_ARCH} (which also
 * admits {@code FINANCE.VIEW}).
 *
 * <p>Read-only since the live test of 24 Sep 2026 (H2): the create, update and
 * delete that used to sit here behind {@code FINANCE_WRITE} let any finance
 * editor rewrite the fee history through the API, and the Fee History screen
 * offered an "Add New" dialog on the archive. Both are gone; the screen shows
 * the log and the API answers 405 to a write. The service keeps its write
 * methods for the quarter close and for the country-scope tests that exercise
 * them; nothing routes to them from the web. {@code StudentArchiveEndpointsTest}
 * holds this, and V139 audits any change made by hand.
 */
@RestController
@RequestMapping("api/v1/studyUniversityFeeArch")
@PreAuthorize(MoheSecurityExpressions.FINANCE_ARCH)
public class StudyUniversityFeeArchController {

    /**
     * Properties of {@link StudyUniversityFeeArch} the list may be ordered by
     * (plan item 2.4). The entity has no {@code id} property: its key is
     * {@code studyUniversityFeeId}, so the old default of {@code sortBy=id} was a 500
     * on every unsorted call.
     */
    static final Set<String> SORTABLE = Set.of(
            "studentId", "studyUniversityFeeId", "studyUniversityFeeStudentName", "studyUniversityFeeFirstYear",
            "studyUniversityFeeAmount", "studyUniversityFeeYear", "studyUniversityFeeDiscount",
            "studyUniversityRealFeeAmount", "studyUniversityFeeCurrency", "studyUniversityFeePayDate",
            "studyUniversityFeePayStatus", "studyUniversityCheckPayorTypeId", "studyUniversityCheckNo",
            "studyUniversityCheckDate", "studyUniversityCheckFormNumber", "studyUniversityFeeMidate",
            "studyUniversityFeeArchDate", "studyCountryInId");

    @Autowired
    private StudyUniversityFeeArchService service;

    @GetMapping("/")
    public Page<StudyUniversityFeeArch> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "studentId") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = SortWhitelist.sort(sortBy, sortDir, SORTABLE);
        Pageable pageable = PageRequest.of(page, size, sort);
        return service.getAll(pageable);
    }

    @GetMapping("/all")
    public List<StudyUniversityFeeArch> getAllWithoutPagination() {
        return service.getAll();
    }

    /** One log row by its key, STUDY_UNIVERSITY_FEE_ID (review F8). */
    @GetMapping("/{id}")
    public ResponseEntity<StudyUniversityFeeArch> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/student/{studentId}")
    public List<StudyUniversityFeeArch> getByStudentId(@PathVariable Integer studentId) {
        return service.getByStudentId(studentId);
    }

    @GetMapping("/search")
    public List<StudyUniversityFeeArch> searchByStudentName(@RequestParam String name) {
        return service.getByStudentName(name);
    }

    @GetMapping("/status/{payStatus}")
    public List<StudyUniversityFeeArch> getByPayStatus(@PathVariable Integer payStatus) {
        return service.getByPayStatus(payStatus);
    }

    @GetMapping("/year/{year}")
    public List<StudyUniversityFeeArch> getByYear(@PathVariable String year) {
        return service.getByYear(year);
    }

    @GetMapping("/distinct-students")
    public List<Object[]> getDistinctStudents() {
        return service.getDistinctStudents();
    }
}