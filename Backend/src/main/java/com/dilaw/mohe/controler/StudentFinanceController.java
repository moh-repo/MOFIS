package com.dilaw.mohe.controler;

import com.dilaw.mohe.approval.model.ApprovalStatus;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.repository.ApprovalRequestRepository;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.approval.service.PendingApprovalExistsException;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentFinancePayment;
import com.dilaw.mohe.model.StudentFinanceWafar;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.repository.StudentFinancePaymentRepository;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.service.StudentAccessGuard;
import com.dilaw.mohe.service.StudentFinanceInputRules;
import com.dilaw.mohe.service.StudentFinanceService;
import com.dilaw.mohe.service.StudentFinanceWafarService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.dilaw.mohe.ums.util.UmsPageableUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.dilaw.mohe.exception.LocalizedMessage;

@RestController
@RequestMapping("api/v1/studentFinance")
@PreAuthorize(MoheSecurityExpressions.FINANCE)
public class StudentFinanceController {

    private static final java.util.Set<String> FINANCE_SORT_FIELDS = java.util.Set.of(
            "id", "studentFinName", "studentFinRecievedYear", "studentFinRecievedMonth",
            "studentFinRecievedAmount", "wafarAmount", "netAmount",
            "studyCountryInId", "studyFinRecievedStatus");

    @Autowired
    private StudentFinanceService service;

    @Autowired
    private com.dilaw.mohe.config.AppTimeZone appTimeZone;

    @Autowired
    private ApprovalIntegrationService approvalIntegrationService;

    @Autowired
    private StudentFinancePaymentRepository paymentRepository;

    @Autowired
    private StudentAccessGuard studentAccessGuard;

    @Autowired
    private StudentFinanceWafarService wafarService;

    @Autowired
    private StudentFinanceRepository financeRepository;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private com.dilaw.mohe.service.StudyFinReceivedStatusService receivedStatuses;

    private static Long getCurrentUserId(HttpServletRequest request) {
        return SecurityRequestUtils.requireUserId();
    }

    /**
     * The form's rules (F3) with the cheque date required only for a status that means
     * money moved, resolved by CODE (live QA H2, 23 Sep 2026).
     */
    private void requireValid(StudentFinance entity) {
        java.util.Set<Integer> paid = receivedStatuses.paidStatusIds();
        StudentFinanceInputRules.require(entity, paid::contains);
    }

    @GetMapping("/")
    public Page<StudentFinance> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        
        Pageable pageable = UmsPageableUtil.of(page, size, sortBy, sortDir, FINANCE_SORT_FIELDS, "id");
        return service.getAll(pageable);
    }

    @GetMapping("/all")
    public List<StudentFinance> getAllWithoutPagination() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentFinance> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getById(id));
    }

    /**
     * What the new-record form needs once a student is picked (review items F3, F4).
     *
     * <p>The defaults the form fills in (amount, study country, period) and the two
     * reasons a new record for this student would be refused: the student already has
     * a finance record (one per student; it is edited, not added again), or a request
     * for it is already awaiting a decision. The finance roles hold neither the student
     * permissions nor APPROVALS, so they could read neither answer from the endpoints
     * that have it; this reads only these few fields, for a student in the caller's
     * scope (403/404 otherwise).
     */
    @GetMapping("/student/{studentId}/new-record")
    public Map<String, Object> newRecordFor(@PathVariable Integer studentId) {
        StudentDatum student = studentAccessGuard.requireStudent(studentId);
        List<ApprovalRequest> pending = approvalRequestRepository.findByEntityTypeAndEntityIdAndStatusIn(
                "STUDENT_FINANCE", String.valueOf(studentId),
                List.of(ApprovalStatus.PENDING, ApprovalStatus.IN_PROGRESS));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("studentId", studentId);
        body.put("studentName", student.getStudentName());
        body.put("studentAmount", student.getStudentAmount());
        body.put("studyCountryInId", student.getStudyCountryIn());
        body.put("addedQuarter", student.getStudentAddedQuarter());
        body.put("hasFinanceRecord", financeRepository.existsById(studentId));
        body.put("pendingRequestId", pending.isEmpty() ? null : pending.get(0).getId());
        body.put("pendingRequestedAt", pending.isEmpty() ? null : pending.get(0).getRequestedAt());
        return body;
    }

    @Autowired
    private com.dilaw.mohe.security.field.FieldAccessService fieldAccess;

    @Autowired
    private org.springframework.context.MessageSource messageSource;

    @PostMapping("/")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> create(@Valid @RequestBody StudentFinance entity, HttpServletRequest request) {
        fieldAccess.assertMayEdit(StudentFinance.class, null, entity, false); // R-18 (D-9 a)
        // F3: the id was typed into the form; it must name a student in the caller's scope.
        requireValid(entity);
        studentAccessGuard.requireStudent(entity.getId());
        if (financeRepository.existsById(entity.getId())) {
            // One finance record per student, keyed by the student: an approved INSERT
            // would overwrite the record on file. It is edited instead.
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(Map.of(
                    "error", "This student already has a finance record; edit that record instead",
                    "code", "FINANCE_EXISTS"));
        }
        try {
            approvalIntegrationService.createStudentFinanceRequest(
                    RequestType.INSERT, entity, null, getCurrentUserId(request));
            return ResponseEntity.ok(Map.of("message", "Request submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (Exception e) {
            return refused(e);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> update(@PathVariable Integer id, @Valid @RequestBody StudentFinance entity, HttpServletRequest request) {
        StudentFinance original = service.getById(id);
        // The record is keyed by the student; the path names it, whatever the body says.
        entity.setId(id);
        fieldAccess.assertMayEdit(StudentFinance.class, original, entity, false); // R-18 (D-9 a)
        requireValid(entity); // F3
        try {
            approvalIntegrationService.createStudentFinanceRequest(
                    RequestType.UPDATE, entity, original, getCurrentUserId(request));
            return ResponseEntity.ok(Map.of("message", "Update request submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (Exception e) {
            return refused(e);
        }
    }

    /**
     * A refusal as 400 { error }. When the record already has an undecided request the
     * body also carries {@code code: PENDING_EXISTS} and that request's id, so the form
     * can say so in the user's language and offer to open it (review item F4).
     */
    private ResponseEntity<?> refused(Exception e) {
        Map<String, Object> body = new LinkedHashMap<>();
        // A keyed refusal in the reader's language (W3-08); anything else as it stands.
        body.put("error", String.valueOf(LocalizedMessage.resolve(messageSource, e,
                org.springframework.context.i18n.LocaleContextHolder.getLocale())));
        if (e instanceof PendingApprovalExistsException pending) {
            body.put("code", "PENDING_EXISTS");
            body.put("requestId", pending.getPendingRequestId());
        }
        return ResponseEntity.badRequest().body(body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> delete(@PathVariable Integer id, HttpServletRequest request) {
        try {
            StudentFinance original = service.getById(id);
            approvalIntegrationService.createStudentFinanceRequest(
                    RequestType.DELETE, original, original, getCurrentUserId(request));
            return ResponseEntity.ok(Map.of("message", "Delete request submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (Exception e) {
            return refused(e);
        }
    }

    /** Per-student read: the student must be in the caller's country scope (403 otherwise). */
    @GetMapping("/payment/byStudent/{studentId}")
    public ResponseEntity<List<StudentFinancePayment>> getPaymentsByStudent(@PathVariable Integer studentId) {
        studentAccessGuard.requireStudent(studentId);
        return ResponseEntity.ok(paymentRepository.findByStudentIdOrderByPaymentIdAsc(studentId));
    }

    /** Status update routed through approval workflow. */
    @PatchMapping("/{id}/status")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> updateStatus(
            @PathVariable Integer id,
            @RequestParam Integer statusId,
            HttpServletRequest request) {
        try {
            StudentFinance original = service.getById(id);
            StudentFinance proposed = copyFinanceForApproval(original);
            proposed.setStudyFinRecievedStatus(statusId);
            // The same rules as the form's save (F3): a status of 0 or a record the
            // form would refuse is refused here too.
            requireValid(proposed);
            approvalIntegrationService.createStudentFinanceRequest(
                    RequestType.UPDATE, proposed, original, getCurrentUserId(request));
            return ResponseEntity.ok(Map.of("message", "Update request submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (Exception e) {
            return refused(e);
        }
    }

    /** Received-whom update routed through approval workflow. */
    @PatchMapping("/{id}/receivedWhom")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> setReceivedWhomInfo(
            @PathVariable Integer id,
            @RequestParam Integer whomId,
            @RequestParam(required = false) Integer statusId,
            HttpServletRequest request) {
        try {
            StudentFinance original = service.getById(id);
            StudentFinance proposed = copyFinanceForApproval(original);
            if (whomId != null) {
                proposed.setStudyReceivedWhomId(whomId);
            }
            if (statusId != null) {
                proposed.setStudyReceivedWhomStatusId(statusId);
            }
            requireValid(proposed); // F3, as updateStatus
            approvalIntegrationService.createStudentFinanceRequest(
                    RequestType.UPDATE, proposed, original, getCurrentUserId(request));
            return ResponseEntity.ok(Map.of("message", "Update request submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (Exception e) {
            return refused(e);
        }
    }

    /**
     * The stored record with one field about to change, as the proposal. Every
     * property, not a hand-picked twenty: the approver's changes view compares the
     * proposal with the record on file, so a field left out — the currency, the
     * bank account, the quarter, the wafar — read as being cleared (QA B12, N10).
     */
    static StudentFinance copyFinanceForApproval(StudentFinance source) {
        StudentFinance copy = new StudentFinance();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }

    /** Upsert wafar record for a student — bypasses approval workflow. */
    @PutMapping("/{id}/wafar")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> upsertWafar(@PathVariable Integer id, @Valid @RequestBody StudentFinanceWafar payload) {
        try {
            payload.setId(id);
            StudentFinanceWafar saved;
            try {
                saved = wafarService.update(id, payload);
            } catch (org.springframework.web.server.ResponseStatusException e) {
                // Only "no row yet" falls through to a create. Any other refusal (a
                // lowered deposit the attaché's balance cannot give up, R5-04) used to be
                // taken for that too, and the create then overwrote the row unchecked.
                if (e.getStatusCode().value() != 404) throw e;
                saved = wafarService.create(payload);
            }
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // the balance 409 keeps its status (R5-04, R5-19)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/wafar")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> deleteWafar(@PathVariable Integer id) {
        try {
            wafarService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // the balance 409, and a 404 for no row, keep their status
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/wafar")
    public ResponseEntity<StudentFinanceWafar> getWafar(@PathVariable Integer id) {
        return ResponseEntity.ok(wafarService.getById(id));
    }

    @GetMapping("/search")
    public Page<StudentFinance> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {

        Pageable pageable = UmsPageableUtil.of(page, size, sortBy, sortDir, FINANCE_SORT_FIELDS, "id");
        // Day boundaries in the application's time zone (mohe.report.timezone), the
        // same zone the reports use — not the server's, which is whatever the box is set to.
        java.time.Instant start = startDate != null ? appTimeZone.startOfDay(startDate) : null;
        java.time.Instant end = endDate != null ? appTimeZone.endOfDay(endDate) : null;
        return service.search(searchTerm, status, start, end, pageable);
    }
} 