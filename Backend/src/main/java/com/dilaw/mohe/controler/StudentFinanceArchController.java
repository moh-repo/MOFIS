package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.StudentFinanceArch;
import com.dilaw.mohe.service.StudentFinanceArchService;
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
 * Archived finance rows. Reads under {@code FINANCE_ARCH} or {@code FINANCE}: the
 * Finance History page opens with FINANCE_ARCH, and the Archiving Officer, who holds
 * that and no FINANCE code, was refused the page's own rows (live QA M6).
 *
 * <p>Read-only, like the student archive. The archive is the audit copy: the
 * quarter close writes it and the Finance Archive screen reads it. The generic
 * create, update and delete this controller used to expose behind
 * {@code FINANCE_WRITE} were reachable by any finance editor although no screen
 * offered them — the live test of 24 Sep 2026 (H2) deleted entry 1, student 1's
 * snapshot of 4 June 2026, with one {@code DELETE} and nothing audited it. They
 * are gone rather than gated, for the reason {@code StudentDataArchController}
 * gives: a gate is only as good as the permission bundle a future role is given.
 * {@code StudentArchiveEndpointsTest} holds that no write comes back. V139 adds
 * the audit trigger for any change made by hand.
 */
@RestController
@RequestMapping("api/v1/studentFinanceArch")
@PreAuthorize(MoheSecurityExpressions.FINANCE_ARCH + " or " + MoheSecurityExpressions.FINANCE)
public class StudentFinanceArchController {

    /** Properties of {@link StudentFinanceArch} the list may be ordered by (plan item 2.4). */
    static final Set<String> SORTABLE = Set.of(
            "financeArchEntryId", "studentId", "studentFinId", "studentFinName", "studentFinRecievedYear",
            "studentFinRecievedMonth", "studentFinRecievedYearMonth", "studentFinRecievedAmount",
            "studyCountryInId", "studyGovInId", "studyFinanceBankeId", "studyFinanceBankBranchId",
            "studyFinanceDofId", "studyCheckId", "studyCheckDate", "studyCheckFormNumber",
            "studyFinRecievedStatus", "studyReceivedWhomStatusId", "studyReceivedWhomId", "studyFinanceMidate",
            "moheQuarterId", "archievedDate", "wafarAmount", "wafarTypeId", "netAmount");

    @Autowired
    private StudentFinanceArchService service;

    @GetMapping("/")
    public Page<StudentFinanceArch> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "studentId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = SortWhitelist.sort(sortBy, sortDir, SORTABLE);
        Pageable pageable = PageRequest.of(page, size, sort);
        return service.getAll(pageable);
    }

    @GetMapping("/all")
    public List<StudentFinanceArch> getAllWithoutPagination() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentFinanceArch> getById(@PathVariable Long id) {
        try {
            StudentFinanceArch studentFinanceArch = service.getById(id);
            return ResponseEntity.ok(studentFinanceArch);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/byStudent/{studentId}")
    public ResponseEntity<List<StudentFinanceArch>> getByStudent(@PathVariable Integer studentId) {
        return ResponseEntity.ok(service.findByStudentId(studentId));
    }
} 