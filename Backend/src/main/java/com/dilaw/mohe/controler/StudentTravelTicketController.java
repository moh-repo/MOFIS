package com.dilaw.mohe.controler;

import com.dilaw.mohe.approval.model.ApprovalStatus;

import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.dto.TravelFundingSourceDto;
import com.dilaw.mohe.model.StudentTravelTicket;
import com.dilaw.mohe.model.StudentTravelTicketFamilyMember;
import com.dilaw.mohe.service.StudentTravelTicketService;
import com.dilaw.mohe.service.TravelFundingSourceService;
import com.dilaw.mohe.service.TravelTicketWafarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Travel tickets. Reads under {@code STUDENT_TRAVEL} (VIEW and EXPORT included);
 * writes carry {@code STUDENT_TRAVEL_WRITE} — the ADD/EDIT/DELETE codes only
 * (plan item 1.5). A method-level annotation replaces the class-level one.
 */
@RestController
@RequestMapping("api/v1/student-travel-tickets")
@PreAuthorize(MoheSecurityExpressions.STUDENT_TRAVEL)
public class StudentTravelTicketController {

    @Autowired
    private StudentTravelTicketService service;

    @Autowired
    private ApprovalIntegrationService approvalIntegrationService;

    @Autowired
    private TravelTicketWafarService travelTicketWafarService;

    @Autowired
    private TravelFundingSourceService fundingSourceService;

    @Autowired
    private com.dilaw.mohe.service.StudentAccessGuard studentAccessGuard;

    @Autowired
    private com.dilaw.mohe.service.StudentDocumentStorageService documentStorage;

    @Autowired
    private com.dilaw.mohe.service.StudentDocumentService studentDocumentService;

    /**
     * Review T3: the ticket as a PDF. Stored in the student's documents, encrypted,
     * and hidden until the ticket it belongs to is approved (the ticket row does
     * not exist before then, so the file goes first and the ticket names it by id).
     * Travel permissions, not student-editing ones: the travel officer attaches it.
     */
    @PostMapping(value = "/document", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MoheSecurityExpressions.STUDENT_TRAVEL_WRITE)
    public ResponseEntity<?> uploadTicketDocument(@RequestParam("studentId") Integer studentId,
                                                  @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        com.dilaw.mohe.model.StudentDatum student = studentAccessGuard.requireStudent(studentId);
        try {
            com.dilaw.mohe.util.FileUploadValidator.validate(file);
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a student the caller may not reach keeps its 403 / 404 (N14)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);
        if (!"application/pdf".equalsIgnoreCase(file.getContentType()) && !name.endsWith(".pdf")) {
            return ResponseEntity.badRequest().body(Map.of("error", "The ticket must be a PDF file"));
        }
        try {
            com.dilaw.mohe.model.StudentDocument doc = documentStorage.store(studentId, student.getStudyCountryIn(),
                    file, StudentTravelTicketService.TICKET_DOCUMENT_TYPE, StudentTravelTicketService.TICKET_DOCUMENT_TYPE);
            doc.setIsActive(false);
            doc = studentDocumentService.saveDocument(doc);
            return ResponseEntity.ok(Map.of(
                    "documentId", doc.getDocumentId(),
                    "originalFilename", doc.getOriginalFilename() == null ? "" : doc.getOriginalFilename()));
        } catch (java.io.IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "The ticket could not be stored"));
        }
    }

    /**
     * A ticket's PDF, inline. Only documents of the ticket type: this endpoint carries
     * the travel permissions, and must not open the rest of a student's file (passport,
     * certificates) to someone without the student permissions.
     */
    @GetMapping("/document/{documentId}")
    @com.dilaw.mohe.audit.AuditedRead(resource = "TRAVEL_TICKET_DOCUMENT", format = "VIEW")
    public ResponseEntity<?> viewTicketDocument(@PathVariable Integer documentId) {
        com.dilaw.mohe.model.StudentDocument doc = studentAccessGuard.requireDocument(documentId);
        if (!StudentTravelTicketService.TICKET_DOCUMENT_TYPE.equals(doc.getDocumentType())) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] content = documentStorage.read(doc);
            if (content == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(content);
        } catch (java.io.IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Reference data (id, code and names), read like any lookup table (live QA M6): the
     * Pending Approvals page labels a travel-ticket request's funding source with it, and
     * the approvers of those requests need not hold a travel-ticket permission.
     */
    @GetMapping("/funding-sources")
    @PreAuthorize(MoheSecurityExpressions.LOOKUP_TABLE)
    public ResponseEntity<List<TravelFundingSourceDto>> getFundingSources(
            @RequestHeader(name = "Accept-Language", required = false) String lang) {
        return ResponseEntity.ok(fundingSourceService.getActiveFundingSources(lang));
    }

    /**
     * The collection, narrowed by the optional {@code studentId} and {@code travelType}
     * query parameters the Travel Tickets page's filter bar sends. Without them,
     * everything in the caller's scope, as before.
     */
    @GetMapping({"", "/"})
    public ResponseEntity<List<StudentTravelTicket>> getAll(
            @RequestParam(name = "studentId", required = false) Integer studentId,
            @RequestParam(name = "travelType", required = false) String travelType) {
        return ResponseEntity.ok(service.search(studentId, travelType));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentTravelTicket> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<StudentTravelTicket>> getByStudentId(@PathVariable Integer studentId) {
        return ResponseEntity.ok(service.getByStudentId(studentId));
    }

    @GetMapping("/type/{travelType}")
    public ResponseEntity<List<StudentTravelTicket>> getByTravelType(@PathVariable String travelType) {
        return ResponseEntity.ok(service.getByTravelType(travelType));
    }

    /**
     * Both spellings of the collection URL, as {@link #getAll()} already accepts.
     * The Add Travel Ticket dialog posts to the URL without the trailing slash;
     * with only {@code "/"} mapped the request died with 405 and no ticket could
     * be created from the screen (live QA H4, 24 Sep 2026).
     */
    @PostMapping({"", "/"})
    @PreAuthorize(MoheSecurityExpressions.STUDENT_TRAVEL_WRITE)
    public ResponseEntity<?> create(@RequestBody StudentTravelTicket entity) {
        try {
            Long userId = SecurityRequestUtils.requireUserId();
            if (entity.getDocumentId() != null) {
                service.requireTicketDocument(entity.getStudentId(), entity.getDocumentId());
            }
            // When funded from Wafar, verify the balance covers the total before queuing for approval.
            if (travelTicketWafarService.isWafarSource(entity.getFundingSourceId())) {
                BigDecimal total = TravelTicketWafarService.computeTotal(entity);
                travelTicketWafarService.assertBalanceCovers(total, null, entity.getStudentId());
            }
            approvalIntegrationService.createStudentTravelTicketRequest(RequestType.INSERT, entity, null, userId);
            return ResponseEntity.ok(Map.of("message", "Travel ticket request submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a student the caller may not reach keeps its 403 / 404 (N14)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_TRAVEL_WRITE)
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody StudentTravelTicket entity) {
        try {
            Long userId = SecurityRequestUtils.requireUserId();
            if (entity.getDocumentId() != null) {
                service.requireTicketDocument(entity.getStudentId(), entity.getDocumentId());
            }
            if (travelTicketWafarService.isWafarSource(entity.getFundingSourceId())) {
                BigDecimal total = TravelTicketWafarService.computeTotal(entity);
                travelTicketWafarService.assertBalanceCovers(total, id, entity.getStudentId());
            }
            approvalIntegrationService.createStudentTravelTicketRequest(RequestType.UPDATE, entity, id, userId);
            return ResponseEntity.ok(Map.of("message", "Update request submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a student the caller may not reach keeps its 403 / 404 (N14)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_TRAVEL_WRITE)
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        try {
            Long userId = SecurityRequestUtils.requireUserId();
            StudentTravelTicket existing = service.getById(id);
            // Minimal snapshot: avoids serializing lazy family members outside the transaction.
            StudentTravelTicket snapshot = new StudentTravelTicket();
            snapshot.setTravelTicketId(existing.getTravelTicketId());
            snapshot.setStudentId(existing.getStudentId());
            snapshot.setTravelType(existing.getTravelType());
            approvalIntegrationService.createStudentTravelTicketRequest(RequestType.DELETE, snapshot, id, userId);
            return ResponseEntity.ok(Map.of("message", "Delete request submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a student the caller may not reach keeps its 403 / 404 (N14)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{ticketId}/family-members")
    public ResponseEntity<List<StudentTravelTicketFamilyMember>> getFamilyMembers(@PathVariable Integer ticketId) {
        return ResponseEntity.ok(service.getFamilyMembersByTicketId(ticketId));
    }
}