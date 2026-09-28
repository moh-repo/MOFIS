package com.dilaw.mohe.approval.service;

import com.dilaw.mohe.approval.event.ApprovalExecutionEvent;
import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.dto.OnboardingParsedInsert;
import com.dilaw.mohe.dto.StudentExtensionRequest;
import com.dilaw.mohe.dto.StudentOnboardingPayload;
import com.dilaw.mohe.dto.StudentParentDto;
import com.dilaw.mohe.dto.StudentRelativeDto;
import com.dilaw.mohe.model.StudentBooksAllowance;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentFinancePayment;
import com.dilaw.mohe.model.StudentFinanceWafar;
import com.dilaw.mohe.model.StudentLoan;
import com.dilaw.mohe.model.StudentTravelTicket;
import com.dilaw.mohe.model.StudyFinReceivedWhom;
import com.dilaw.mohe.model.StudyUniversityFee;
import com.dilaw.mohe.repository.StudentFinancePaymentRepository;
import com.dilaw.mohe.service.StudentBooksAllowanceService;
import com.dilaw.mohe.service.StudentLoanService;
import com.dilaw.mohe.service.StudyFinReceivedWhomService;
import com.dilaw.mohe.service.PendingOnboardingUploadService;
import com.dilaw.mohe.service.StudentDataService;
import com.dilaw.mohe.service.StudentDocumentService;
import com.dilaw.mohe.service.StudentDocumentRemovalService;
import com.dilaw.mohe.service.StudentExtensionService;
import com.dilaw.mohe.service.StudentFamilyService;
import com.dilaw.mohe.service.StudentFinanceService;
import com.dilaw.mohe.service.StudentFinanceWafarService;
import com.dilaw.mohe.service.StudentTravelTicketService;
import com.dilaw.mohe.service.TravelTicketWafarService;
import com.dilaw.mohe.service.StudyUniversityFeeService;
import com.dilaw.mohe.service.StudentYearResultService;
import com.dilaw.mohe.util.StudentFinanceDraftMapper;
import com.dilaw.mohe.util.StudentFinanceLedgerMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

/**
 * Service to integrate the approval workflow with actual entity operations.
 * Listens for ApprovalExecutionEvent and applies changes to corresponding
 * entities.
 */
@Service
public class ApprovalIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalIntegrationService.class);

    @Autowired
    private ApprovalWorkflowService workflowService;

    @Autowired
    private StudentDataService studentDataService;

    @Autowired
    private StudentFinanceService studentFinanceService;

    @Autowired
    private StudentFinanceWafarService studentFinanceWafarService;

    @Autowired
    private StudyUniversityFeeService studyUniversityFeeService;

    @Autowired
    private StudentExtensionService studentExtensionService;

    @Autowired
    private StudentTravelTicketService studentTravelTicketService;

    @Autowired(required = false)
    private com.dilaw.mohe.service.TravelTicketDocumentCleanup ticketDocumentCleanup;

    @Autowired
    private TravelTicketWafarService travelTicketWafarService;

    @Autowired
    private PendingOnboardingUploadService pendingOnboardingUploadService;

    @Autowired
    private StudentDocumentService studentDocumentService;

    @Autowired
    private StudentDocumentRemovalService studentDocumentRemovalService;

    @Autowired
    private StudentFinancePaymentRepository studentFinancePaymentRepository;

    @Autowired
    private StudentYearResultService studentYearResultService;

    @Autowired
    private StudentLoanService studentLoanService;

    @Autowired
    private StudentBooksAllowanceService studentBooksAllowanceService;

    @Autowired
    private StudyFinReceivedWhomService studyFinReceivedWhomService;

    @Autowired
    private StudentFamilyService studentFamilyService;

    private final ObjectMapper objectMapper;

    public ApprovalIntegrationService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        /*
         * Dates must serialize as ISO text here exactly as they do in the controllers that
         * build the proposed record. With timestamps left on, the original side of a request
         * stored an epoch number while the proposed side stored a string, so every date
         * column compared unequal and showed up as a change on every single edit.
         */
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * @see #createStudentDataRequest(RequestType, StudentDatum, StudentDatum, StudentFinance, String, Long)
     */
    @Transactional
    public ApprovalRequest createStudentDataRequest(RequestType requestType,
            StudentDatum proposedData, StudentDatum originalData, Long requestedBy) {
        return createStudentDataRequest(requestType, proposedData, originalData, null, null, requestedBy);
    }

    /**
     * INSERT may include optional finance draft + pending onboarding upload session ID.
     */
    @Transactional
    public ApprovalRequest createStudentDataRequest(RequestType requestType,
            StudentDatum proposedData, StudentDatum originalData,
            StudentFinance linkedFinanceDraft, String pendingUploadSessionId,
            Long requestedBy) {
        return createStudentDataRequest(requestType, proposedData, originalData,
                linkedFinanceDraft, pendingUploadSessionId, null, null, requestedBy);
    }

    /**
     * INSERT from the Add Student wizard: also carries the parents and relatives typed
     * into it, which are written when the request is executed.
     */
    @Transactional
    public ApprovalRequest createStudentDataRequest(RequestType requestType,
            StudentDatum proposedData, StudentDatum originalData,
            StudentFinance linkedFinanceDraft, String pendingUploadSessionId,
            List<StudentParentDto> parents, List<StudentRelativeDto> relatives,
            Long requestedBy) {
        try {
            /*
             * entityId scopes duplicate-check rows to one student (see ApprovalWorkflowService).
             * UPDATE must not pass null — otherwise Hibernate matches STUDENT_DATA with EntityId IS NULL,
             * falsely blocking edits when another new-student onboarding is pending.
             */
            String entityId = null;
            if (originalData != null && originalData.getStudentId() != null) {
                entityId = String.valueOf(originalData.getStudentId());
            } else if (requestType != RequestType.INSERT
                    && proposedData != null
                    && proposedData.getStudentId() != null
                    && proposedData.getStudentId() > 0) {
                entityId = String.valueOf(proposedData.getStudentId());
            }
            String origData = originalData != null ? objectMapper.writeValueAsString(originalData) : null;
            // Use the study destination country (STUDY_COUNTRY_IN), not nationality (STUDENT_COUNTRY_ID),
            // to match the scope filter used across all student queries.
            Integer countryId = proposedData != null ? proposedData.getStudyCountryIn() : null;

            /*
             * The envelope also carries staged uploads for UPDATE, so a new photo or new
             * documents stay pending with the rest of the edit and are written only once
             * the request is approved.
             */
            final String requestData;
            if (requestType == RequestType.INSERT
                    || (pendingUploadSessionId != null && !pendingUploadSessionId.isBlank())) {
                StudentOnboardingPayload p = new StudentOnboardingPayload();
                p.setStudentData(proposedData);
                p.setLinkedFinanceDraft(linkedFinanceDraft);
                p.setPendingUploadSessionId(pendingUploadSessionId);
                if (requestType == RequestType.INSERT) {
                    p.setParents(parents == null || parents.isEmpty() ? null : parents);
                    p.setRelatives(relatives == null || relatives.isEmpty() ? null : relatives);
                }
                requestData = objectMapper.writeValueAsString(p);
            } else {
                requestData = objectMapper.writeValueAsString(proposedData);
            }

            if (requestType == RequestType.UPDATE && entityId != null) {
                return workflowService.createOrReplacePendingRequest(
                        "STUDENT_DATA", requestType, entityId, requestData,
                        origData, requestedBy, countryId);
            }
            return workflowService.createRequest(
                    "STUDENT_DATA",
                    requestType,
                    entityId,
                    requestData,
                    origData,
                    requestedBy,
                    countryId,
                    null);
        } catch (PendingApprovalExistsException | IllegalArgumentException
                 | org.springframework.web.server.ResponseStatusException
                 | org.springframework.security.access.AccessDeniedException refused) {
            // A refusal is answered as itself, not as a 500 (W4-08): the pending-request one
            // names the request the screen can open.
            throw refused;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create approval request: " + e.getMessage(), e);
        }
    }

    /**
     * Create an approval request for a student finance (STUDENT_FINANCE) operation.
     */
    @Transactional
    public ApprovalRequest createStudentFinanceRequest(RequestType requestType,
            StudentFinance proposedData, StudentFinance originalData, Long requestedBy) {
        return createStudentFinanceRequest(requestType, proposedData, originalData, requestedBy, null, null);
    }

    /**
     * Puts both sides of a finance request in the shape the approver compares
     * (QA B12, N10, live #13). The changes view diffs every field on either side,
     * so a field one side cannot carry read as a change nobody made:
     *
     * <ul>
     *   <li>{@code netAmount} is computed by the database and read-only in JSON, so
     *   a proposal never had one and every edit showed "net 1200 → (empty)". The
     *   proposal now carries the net the database will compute from it — which also
     *   makes a wafar change visible as the change in net it causes.</li>
     *   <li>the wafar note lives on STUDENT_FINANCE_WAFAR, so the stored side never
     *   had one and an untouched note read as newly added. The stored side is given
     *   the note on file; a proposal without a note keeps it, as the executor does
     *   ({@code syncFromFinance} writes a note only when one is sent).</li>
     * </ul>
     */
    private void alignForComparison(StudentFinance proposed, StudentFinance original) {
        if (proposed == null) {
            return;
        }
        proposed.setNetAmount(nz(proposed.getStudentFinRecievedAmount()) - nz(proposed.getWafarAmount()));
        if (original != null && original.getWafarNote() == null) {
            original.setWafarNote(studentFinanceWafarService.noteOf(original.getId()));
        }
        if (original != null && proposed.getWafarNote() == null) {
            proposed.setWafarNote(original.getWafarNote());
        }
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /** Optional parent STUDENT_DATA request ID when chaining after onboarding. */
    @Transactional
    public ApprovalRequest createStudentFinanceRequest(RequestType requestType,
            StudentFinance proposedData, StudentFinance originalData, Long requestedBy,
            Integer countryId,
            Long parentApprovalRequestId) {
        // R6-01: a finance edit that lowers, drops or moves the student's Wafar, or a finance
        // delete, must leave the attaché's approved spending covered - checked now, as the
        // Wafar requests are, and again when the request executes.
        if (requestType == RequestType.UPDATE || requestType == RequestType.DELETE) {
            Integer studentId = originalData != null ? originalData.getId()
                    : (proposedData != null ? proposedData.getId() : null);
            studentFinanceWafarService.requireWithdrawableByFinance(studentId,
                    requestType == RequestType.DELETE ? null : proposedData);
        }
        try {
            String entityId = originalData != null ? String.valueOf(originalData.getId()) : null;
            if (entityId == null && proposedData.getId() != null) {
                entityId = String.valueOf(proposedData.getId());
            }
            alignForComparison(proposedData, originalData);
            String requestData = objectMapper.writeValueAsString(proposedData);
            String origData = originalData != null ? objectMapper.writeValueAsString(originalData) : null;
            /*
             * Every other creator in this class resolves the country before handing the
             * request over; this one alone left it to the caller, and the four-argument
             * overload passes null. ApprovalWorkflowService then falls back to the
             * requester's own attaché, which is nothing when the requester holds more
             * than one country — and since V065 put ApprovalRequests under the tenant
             * policy, a request with no country is invisible to everyone but a ministry
             * account, including the person who raised it. Request 20155 on the
             * development database is one of those.
             *
             * The row's own country first (it is the column the scope filter reads),
             * then the row it replaces, then the student's. Null only when the request
             * names no student at all.
             */
            Integer scopedCountryId = countryId;
            if (scopedCountryId == null && proposedData != null) {
                scopedCountryId = proposedData.getStudyCountryInId();
            }
            if (scopedCountryId == null && originalData != null) {
                scopedCountryId = originalData.getStudyCountryInId();
            }
            if (scopedCountryId == null) {
                Integer studentId = originalData != null ? originalData.getId()
                        : (proposedData != null ? proposedData.getId() : null);
                scopedCountryId = resolveStudyCountryIn(studentId);
            }
            return workflowService.createRequest(
                    "STUDENT_FINANCE",
                    requestType,
                    entityId,
                    requestData,
                    origData,
                    requestedBy,
                    scopedCountryId,
                    parentApprovalRequestId);
        } catch (PendingApprovalExistsException | IllegalArgumentException
                 | org.springframework.web.server.ResponseStatusException
                 | org.springframework.security.access.AccessDeniedException refused) {
            // A refusal is answered as itself, not as a 500 (W4-08): the pending-request one
            // names the request the screen can open.
            throw refused;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create approval request: " + e.getMessage(), e);
        }
    }

    /**
     * Create an approval request for a wafar record edited directly on the wafar page.
     * Wafar reaching the table from the finance form goes through the STUDENT_FINANCE
     * request instead — see executeStudentFinanceRequest.
     */
    @Transactional
    public ApprovalRequest createStudentFinanceWafarRequest(RequestType requestType,
            StudentFinanceWafar proposedData, StudentFinanceWafar originalData, Long requestedBy) {
        /*
         * Validated here, outside the try, so the user is told what is wrong the moment they
         * save rather than after an approver has signed off on something unapplyable — and
         * so the message reaches them intact instead of wrapped in "Failed to create
         * approval request:".
         */
        if (requestType != RequestType.DELETE) {
            studentFinanceWafarService.validateAgainstFinance(proposedData);
        }
        // R5-04: a deposit deleted, lowered or moved must leave its attaché's approved
        // spending covered. Checked again when the request executes.
        if (requestType == RequestType.DELETE) {
            studentFinanceWafarService.requireWithdrawable(originalData, null, 0);
        } else if (requestType == RequestType.UPDATE && proposedData != null) {
            studentFinanceWafarService.requireWithdrawable(originalData, proposedData.getStudyCountryInId(),
                    proposedData.getStudentFinRecievedAmount() == null ? 0 : proposedData.getStudentFinRecievedAmount());
        }
        // N-04: only the student's own attaché may raise a Wafar change; ministry
        // accounts are refused here, before the request exists, with a 403 that
        // reaches the caller intact (the try below would wrap it as a 400).
        {
            Integer studentId = proposedData != null ? proposedData.getId()
                    : (originalData != null ? originalData.getId() : null);
            countryScopeService.assertCountryOwnedWrite(resolveStudyCountryIn(studentId), "Wafar finance");
        }
        try {
            Integer studentId = proposedData != null ? proposedData.getId()
                    : (originalData != null ? originalData.getId() : null);
            String entityId = studentId != null ? String.valueOf(studentId) : null;
            String requestData = objectMapper.writeValueAsString(proposedData);
            String origData = originalData != null ? objectMapper.writeValueAsString(originalData) : null;
            Integer countryId = resolveStudyCountryIn(studentId);
            return workflowService.createRequest(
                    "STUDENT_FINANCE_WAFAR",
                    requestType,
                    entityId,
                    requestData,
                    origData,
                    requestedBy,
                    countryId);
        } catch (PendingApprovalExistsException | IllegalArgumentException
                 | org.springframework.web.server.ResponseStatusException
                 | org.springframework.security.access.AccessDeniedException refused) {
            // A refusal is answered as itself, not as a 500 (W4-08): the pending-request one
            // names the request the screen can open.
            throw refused;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create approval request: " + e.getMessage(), e);
        }
    }

    /**
     * Create an approval request for a study university fee operation.
     */
    @Transactional
    public ApprovalRequest createStudyUniversityFeeRequest(RequestType requestType,
            StudyUniversityFee proposedData, StudyUniversityFee originalData, Long requestedBy) {
        try {
            String entityId = originalData != null ? String.valueOf(originalData.getId()) : null;
            String requestData = objectMapper.writeValueAsString(proposedData);
            String origData = originalData != null ? objectMapper.writeValueAsString(originalData) : null;
            Integer countryId = resolveStudyCountryIn(proposedData != null ? proposedData.getStudentId() : null);
            return workflowService.createRequest(
                    "STUDY_UNIVERSITY_FEE",
                    requestType,
                    entityId,
                    requestData,
                    origData,
                    requestedBy,
                    countryId);
        } catch (PendingApprovalExistsException | IllegalArgumentException
                 | org.springframework.web.server.ResponseStatusException
                 | org.springframework.security.access.AccessDeniedException refused) {
            // A refusal is answered as itself, not as a 500 (W4-08): the pending-request one
            // names the request the screen can open.
            throw refused;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create approval request: " + e.getMessage(), e);
        }
    }

    /**
     * Create an approval request for a student extension operation.
     * For INSERT, proposedRequest is the payload; for UPDATE/DELETE, extensionId identifies the record.
     */
    @Transactional
    public ApprovalRequest createStudentExtensionRequest(RequestType requestType,
            StudentExtensionRequest proposedRequest, Integer extensionId, Long requestedBy) {
        try {
            String entityId = extensionId != null ? String.valueOf(extensionId) : null;
            // Stamp the student's name into the payload so approvers can see who the request is for.
            resolveStudentNameInto(proposedRequest);
            String requestData = objectMapper.writeValueAsString(proposedRequest);
            Integer countryId = resolveStudyCountryIn(proposedRequest != null ? proposedRequest.getStudentId() : null);

            /*
             * Editing an extension that already has a PENDING request should refresh that request
             * with the latest values rather than fail with "already a pending request" — same
             * behaviour as STUDENT_DATA. INSERT/DELETE keep the plain create path.
             */
            if (requestType == RequestType.UPDATE && entityId != null) {
                return workflowService.createOrReplacePendingRequest(
                        "STUDENT_EXTENSION", requestType, entityId, requestData, null, requestedBy, countryId);
            }
            return workflowService.createRequest(
                    "STUDENT_EXTENSION",
                    requestType,
                    entityId,
                    requestData,
                    null,
                    requestedBy,
                    countryId);
        } catch (PendingApprovalExistsException | IllegalArgumentException
                 | org.springframework.web.server.ResponseStatusException
                 | org.springframework.security.access.AccessDeniedException refused) {
            // A refusal is answered as itself, not as a 500 (W4-08): the pending-request one
            // names the request the screen can open.
            throw refused;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create approval request: " + e.getMessage(), e);
        }
    }

    /**
     * Create an approval request for a student travel ticket operation (with its family members).
     * For INSERT, proposed is the payload; for UPDATE/DELETE, ticketId identifies the record.
     */
    @Transactional
    public ApprovalRequest createStudentTravelTicketRequest(RequestType requestType,
            StudentTravelTicket proposed, Integer ticketId, Long requestedBy) {
        try {
            String entityId = ticketId != null ? String.valueOf(ticketId) : null;
            // Stamp the student's name into the payload so approvers can see who the ticket is for.
            if (proposed != null && (proposed.getStudentName() == null || proposed.getStudentName().isBlank())) {
                proposed.setStudentName(resolveStudentName(proposed.getStudentId()));
            }
            String requestData = objectMapper.writeValueAsString(proposed);
            Integer countryId = resolveStudyCountryIn(proposed != null ? proposed.getStudentId() : null);

            // Re-editing a ticket that already has a PENDING request refreshes it instead of failing.
            if (requestType == RequestType.UPDATE && entityId != null) {
                Integer previousDocument = ticketDocumentCleanup == null ? null
                        : ticketDocumentCleanup.documentOfOpenRequest(entityId);
                ApprovalRequest replaced = workflowService.createOrReplacePendingRequest(
                        "STUDENT_TRAVEL_TICKET", requestType, entityId, requestData, null, requestedBy, countryId);
                // The pending edit attached another PDF: the one it replaced is nobody's now (T3, W4-09).
                if (previousDocument != null && !previousDocument.equals(proposed == null ? null : proposed.getDocumentId())) {
                    ticketDocumentCleanup.removeAfterCommit(previousDocument);
                }
                return replaced;
            }
            return workflowService.createRequest(
                    "STUDENT_TRAVEL_TICKET",
                    requestType,
                    entityId,
                    requestData,
                    null,
                    requestedBy,
                    countryId);
        } catch (PendingApprovalExistsException | IllegalArgumentException
                 | org.springframework.web.server.ResponseStatusException
                 | org.springframework.security.access.AccessDeniedException refused) {
            // A refusal is answered as itself, not as a 500 (W4-08): the pending-request one
            // names the request the screen can open.
            throw refused;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create approval request: " + e.getMessage(), e);
        }
    }

    // ── Finance records: loans, books allowances, payment recipients ─────────
    //
    // These three screens submit the dialog's own payload. An edit is stored as
    // the JSON tree the dialog sent rather than a bound entity, because all
    // three services take update(id, JsonNode) precisely so an absent column is
    // not a cleared one — a field that is absent is not a field set to nothing.
    // Replaying the tree at execution keeps that distinction, and it is also
    // what makes the approver's diff show only the fields the dialog changed.

    /** Opens a request for a student loan. {@code body} is the edit's own JSON on UPDATE. */
    @Transactional
    public ApprovalRequest createStudentLoanRequest(RequestType requestType,
            StudentLoan proposed, JsonNode body, Integer loanId, StudentLoan original, Long requestedBy) {
        return createFinanceRecordRequest("STUDENT_LOAN", requestType,
                proposed, body, loanId, original,
                proposed != null ? proposed.getStudentId()
                        : (original != null ? original.getStudentId() : null),
                requestedBy);
    }

    /** Opens a request for a books allowance. {@code body} is the edit's own JSON on UPDATE. */
    @Transactional
    public ApprovalRequest createStudentBooksAllowanceRequest(RequestType requestType,
            StudentBooksAllowance proposed, JsonNode body, Integer booksAllowId,
            StudentBooksAllowance original, Long requestedBy) {
        return createFinanceRecordRequest("STUDENT_BOOKS_ALLOWANCE", requestType,
                proposed, body, booksAllowId, original,
                proposed != null ? proposed.getStudentId()
                        : (original != null ? original.getStudentId() : null),
                requestedBy);
    }

    /** Opens a request for a finance payment recipient. {@code body} is the edit's own JSON on UPDATE. */
    @Transactional
    public ApprovalRequest createStudyFinReceivedWhomRequest(RequestType requestType,
            StudyFinReceivedWhom proposed, JsonNode body, Integer whomId,
            StudyFinReceivedWhom original, Long requestedBy) {
        return createFinanceRecordRequest("STUDY_FIN_RECEIVED_WHOM", requestType,
                proposed, body, whomId, original,
                proposed != null ? proposed.getStudentForDelegationId()
                        : (original != null ? original.getStudentForDelegationId() : null),
                requestedBy);
    }

    /**
     * The one shape all three share: an INSERT carries the bound entity, an
     * UPDATE carries the dialog's tree, a DELETE carries the row as it stands.
     */
    private ApprovalRequest createFinanceRecordRequest(String entityType, RequestType requestType,
            Object proposed, JsonNode body, Integer recordId, Object original,
            Integer studentId, Long requestedBy) {
        try {
            String entityId = recordId != null ? String.valueOf(recordId) : null;
            String requestData = requestType == RequestType.UPDATE && body != null
                    ? objectMapper.writeValueAsString(body)
                    : objectMapper.writeValueAsString(proposed);
            String origData = original != null ? objectMapper.writeValueAsString(original) : null;
            Integer countryId = resolveStudyCountryIn(studentId);
            return workflowService.createRequest(
                    entityType, requestType, entityId, requestData, origData, requestedBy, countryId);
        } catch (PendingApprovalExistsException | IllegalArgumentException
                 | org.springframework.web.server.ResponseStatusException
                 | org.springframework.security.access.AccessDeniedException refused) {
            // A refusal is answered as itself, not as a 500 (W4-08): the pending-request one
            // names the request the screen can open.
            throw refused;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create approval request: " + e.getMessage(), e);
        }
    }

    /**
     * Event listener that executes approved requests by applying the actual
     * changes.
     * Called automatically when ApprovalWorkflowService publishes
     * ApprovalExecutionEvent.
     */
    /**
     * Entity types whose execution writes Wafar rows, directly or as a side
     * effect (finance sync, university-fee discount, Wafar-funded tickets).
     */
    private static final java.util.Set<String> WAFAR_WRITING_ENTITY_TYPES = java.util.Set.of(
            "STUDENT_FINANCE_WAFAR", "STUDENT_FINANCE", "STUDY_UNIVERSITY_FEE", "STUDENT_TRAVEL_TICKET",
            com.dilaw.mohe.wafar.service.WafarSpendingService.CORRECTION_ENTITY_TYPE);

    /** N-04.e: applies an approved ministry correction to a Wafar spend. */
    @Autowired
    @org.springframework.context.annotation.Lazy
    private com.dilaw.mohe.wafar.service.WafarSpendingService wafarSpendingService;

    @Autowired
    private com.dilaw.mohe.config.tenant.WafarWriteKey wafarWriteKey;

    @Autowired
    private com.dilaw.mohe.ums.service.PrivilegedChangeService privilegedChangeService;

    @Autowired
    private com.dilaw.mohe.ums.service.UmsAuditLogService umsAuditLogService;

    @EventListener
    @Transactional
    public void handleApprovalExecution(ApprovalExecutionEvent event) {
        ApprovalRequest request = event.getRequest();
        logger.info("Executing approved request: id={}, type={}, entityType={}",
                request.getId(), request.getRequestType(), request.getEntityType());

        // N-04.d: a ministry-level executor's connection cannot write Wafar rows. The
        // workflow is the control here — the attaché raised the request and the chain
        // signed it — so the key is raised for this transaction and the use is audited.
        if (WAFAR_WRITING_ENTITY_TYPES.contains(request.getEntityType()) && countryScopeService.isMinistryLevelActor()) {
            wafarWriteKey.elevateCurrentTransaction("approval request " + request.getId() + " ("
                    + request.getEntityType() + ") executed by a ministry-level account");
            umsAuditLogService.log(event.getExecutedBy(), "WAFAR_WRITE_ELEVATED", "APPROVAL_REQUEST",
                    request.getId(), request.getEntityType(), "executed by ministry-level account");
        }

        try {
            switch (request.getEntityType()) {
                case "STUDENT_DATA":
                    executeStudentDataRequest(request);
                    break;
                case "STUDENT_FINANCE":
                    executeStudentFinanceRequest(request);
                    break;
                case "STUDENT_FINANCE_WAFAR":
                    executeStudentFinanceWafarRequest(request);
                    break;
                case "STUDY_UNIVERSITY_FEE":
                    executeStudyUniversityFeeRequest(request);
                    break;
                case "STUDENT_DOCUMENT":
                    executeStudentDocumentRequest(request);
                    break;
                case "STUDENT_EXTENSION":
                    executeStudentExtensionRequest(request);
                    break;
                case "STUDENT_TRAVEL_TICKET":
                    executeStudentTravelTicketRequest(request);
                    break;
                case "STUDENT_LOAN":
                    executeStudentLoanRequest(request);
                    break;
                case "STUDENT_BOOKS_ALLOWANCE":
                    executeStudentBooksAllowanceRequest(request);
                    break;
                case "STUDY_FIN_RECEIVED_WHOM":
                    executeStudyFinReceivedWhomRequest(request);
                    break;
                case "USER_PRIVILEGE_CHANGE":
                    // S-03: the second administrator signed; apply the grant as them.
                    privilegedChangeService.execute(request);
                    break;
                case "WAFAR_CORRECTION":
                    // N-04.e: the attaché's administrator signed; apply the ministry's correction as them.
                    wafarSpendingService.applyCorrection(request);
                    break;
                case "SYSTEM_RESTORE":
                    // S-03: nothing to apply here — the ticket lets the requester run the
                    // restore endpoint with the same file within 24 hours.
                    logger.info("SYSTEM_RESTORE approval id={} executed: restore of {} is now authorised for the requester",
                            request.getId(), request.getEntityId());
                    break;
                case "LOOKUP_TABLE":
                    // Lookup-table changes require manual application by an admin —
                    // the approval is recorded but not auto-executed.
                    logger.warn("LOOKUP_TABLE approval id={} marked APPROVED but requires manual execution. " +
                            "An administrator must apply the change in the lookup management UI.", request.getId());
                    return;

                default:
                    logger.error("Unknown entity type: {} for request {}", request.getEntityType(), request.getId());
            }
            logger.info("Successfully executed request: id={}", request.getId());
        } catch (RuntimeException e) {
            // Rethrown as it is, so GlobalExceptionHandler can tell a constraint failure
            // (a translated 409) from a validation message (400) from a real fault (500).
            // Wrapping it, with the driver's text in the message, is what put SQL on the
            // approver's screen (live QA BRD-002).
            logger.error("Failed to execute approved request {}: {}", request.getId(), e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Failed to execute approved request {}: {}", request.getId(), e.getMessage(), e);
            throw new IllegalStateException("The approved request could not be applied.", e);
        }
    }

    private OnboardingParsedInsert parseStudentDataInsertPayload(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        if (root.hasNonNull("studentData") && root.get("studentData").isObject()) {
            StudentOnboardingPayload envelope = objectMapper.treeToValue(root, StudentOnboardingPayload.class);
            return new OnboardingParsedInsert(
                    envelope.getStudentData(),
                    envelope.getLinkedFinanceDraft(),
                    envelope.getPendingUploadSessionId(),
                    envelope.getParents(),
                    envelope.getRelatives());
        }
        StudentDatum solo = objectMapper.readValue(raw, StudentDatum.class);
        return new OnboardingParsedInsert(solo, null, null);
    }

    private void executeStudentDataRequest(ApprovalRequest request) throws Exception {
        StudentDatum studentData;
        if (request.getRequestType() == RequestType.INSERT) {
            OnboardingParsedInsert parsed = parseStudentDataInsertPayload(request.getRequestData());
            studentData = parsed.studentData();

            studentDataService.persistNewApprovedStudent(studentData);
            Integer sid = studentData.getStudentId();
            logger.info("Executed INSERT for student data.id={}", sid);

            if (parsed.pendingUploadSessionId() != null && !parsed.pendingUploadSessionId().isBlank()) {
                try {
                    pendingOnboardingUploadService.promoteToStudent(parsed.pendingUploadSessionId(), sid,
                            studentData.getStudyCountryIn(),
                            studentDataService::updatePicturePath, studentDocumentService);
                } catch (Exception io) {
                    logger.error("Promote onboarding uploads failed for student {}: {}", sid, io.getMessage(), io);
                }
            }

            // Checked when the request was submitted (StudentFamilyService.validateForOnboarding),
            // so a failure here is not expected; it is part of this transaction either way.
            if (parsed.parents() != null && !parsed.parents().isEmpty()) {
                studentFamilyService.replaceParents(sid, parsed.parents());
            }
            if (parsed.relatives() != null && !parsed.relatives().isEmpty()) {
                studentFamilyService.replaceRelatives(sid, parsed.relatives());
            }

            StudentDatum hydrated = studentDataService.getStudentData(sid);
            studentYearResultService.ensureInitialYears(
                    sid,
                    hydrated.getStudentStudyNoOfYears(),
                    hydrated.getStudentStudyStatusId());
            StudentFinance draft = StudentFinanceDraftMapper.mergeDraft(
                    StudentFinanceDraftMapper.fromStudentDatum(hydrated),
                    parsed.linkedFinanceDraft());
            draft.setId(sid);

            /*
             * Always materialize MOHECORE.STUDENT_FINANCE here when STUDENT_DATA INSERT is executed.
             * Previously only a chained STUDENT_FINANCE approval INSERT did seeding — if that workflow
             * was never completed (or queued afterCommit failed silently), finance stayed empty despite
             * an approved student.
             */
            StudentFinanceService.SeedAndMergeOutcome financeSeed =
                    studentFinanceService.seedFromStudentDataAndMergeDraft(sid, draft);
            if (financeSeed.newRowThisRun() > 0) {
                StudentFinancePayment pay = StudentFinanceLedgerMapper.paymentFromFinance(financeSeed.merged(), null);
                if (pay != null) {
                    studentFinancePaymentRepository.save(pay);
                }
            }
            logger.info(
                    "STUDENT_FINANCE baseline after STUDENT_DATA INSERT execute sid={}, newRowThisRun={}, approvalRequestId={}",
                    sid,
                    financeSeed.newRowThisRun(),
                    request.getId());

            /* After STUDENT_DATA approve, ApprovalRequest rows are locked by the EXECUTE txn.
             Inserting STUDENT_FINANCE with FK ParentApprovalRequestId from a REQUIRES_NEW txn
             can block forever on FK parent lock. Queue the chain AFTER COMMIT instead. */
            final String chainedFinanceJson = objectMapper.writeValueAsString(draft);
            final long parentDataRequestId = request.getId();
            final Long chainRequestedBy = request.getRequestedBy();
            final Integer chainCountryId = countryIdForStudentDataApprovalChain(hydrated);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        queueChainedStudentFinanceAfterCommit(
                                chainedFinanceJson, parentDataRequestId, chainRequestedBy, chainCountryId, sid);
                    }
                });
                logger.info("Scheduled chained STUDENT_FINANCE approval AFTER COMMIT (sid={}, parentApprovalRequestId={})",
                        sid, parentDataRequestId);
            } else {
                logger.warn("No txn synchronization; chaining STUDENT_FINANCE synchronously (sid={})", sid);
                queueChainedStudentFinanceAfterCommit(
                        chainedFinanceJson, parentDataRequestId, chainRequestedBy, chainCountryId, sid);
            }
            return;
        }

        // Tolerant parse: UPDATE payloads are enveloped only when uploads travelled with them.
        OnboardingParsedInsert parsedRequest = parseStudentDataInsertPayload(request.getRequestData());
        studentData = parsedRequest.studentData();

        switch (request.getRequestType()) {
            case UPDATE:
                int updateTargetId = resolveStudentDataTargetId(request, studentData);
                studentDataService.update(updateTargetId, studentData);
                logger.info("Executed UPDATE for student id: {} (approvalRequest id={})", updateTargetId, request.getId());

                // Applied after the field update so the promoted picture path is the one kept.
                if (parsedRequest.pendingUploadSessionId() != null
                        && !parsedRequest.pendingUploadSessionId().isBlank()) {
                    try {
                        StudentDatum updated = studentDataService.getStudentData(updateTargetId);
                        pendingOnboardingUploadService.promoteToStudent(
                                parsedRequest.pendingUploadSessionId(),
                                updateTargetId,
                                updated != null ? updated.getStudyCountryIn() : null,
                                studentDataService::updatePicturePath,
                                studentDocumentService);
                        logger.info("Promoted staged uploads for updated student {}", updateTargetId);
                    } catch (Exception io) {
                        logger.error("Promote uploads failed for updated student {}: {}",
                                updateTargetId, io.getMessage(), io);
                    }
                }
                break;
            case DELETE:
                int deleteTargetId = resolveStudentDataTargetId(request, studentData);
                studentDataService.delete(deleteTargetId);
                logger.info("Executed DELETE for student id: {} (approvalRequest id={})", deleteTargetId, request.getId());
                break;
        }
    }

    /**
     * UPDATE/DELETE requests created before {@link ApprovalRequest#getEntityId()} was populated may have a null EntityId.
     * Fall back to studentId inside RequestData or OriginalData.
     */
    private int resolveStudentDataTargetId(ApprovalRequest request, StudentDatum fromRequestData) throws Exception {
        if (request.getEntityId() != null && !request.getEntityId().isBlank()) {
            return Integer.parseInt(request.getEntityId().trim());
        }
        if (fromRequestData != null && fromRequestData.getStudentId() != null && fromRequestData.getStudentId() > 0) {
            logger.warn(
                    "STUDENT_DATA {} ApprovalRequest id={} has blank EntityId; using studentId from RequestData ({})",
                    request.getRequestType(), request.getId(), fromRequestData.getStudentId());
            return fromRequestData.getStudentId();
        }
        Integer fromFlexiblePayload = extractStudentIdFromJsonDocument(request.getRequestData());
        if (fromFlexiblePayload != null && fromFlexiblePayload > 0) {
            logger.warn(
                    "STUDENT_DATA {} ApprovalRequest id={} has blank EntityId; parsing student id from RequestData JSON ({})",
                    request.getRequestType(), request.getId(), fromFlexiblePayload);
            return fromFlexiblePayload;
        }
        if (request.getOriginalData() != null && !request.getOriginalData().isBlank()) {
            StudentDatum orig = objectMapper.readValue(request.getOriginalData(), StudentDatum.class);
            if (orig != null && orig.getStudentId() != null && orig.getStudentId() > 0) {
                logger.warn(
                        "STUDENT_DATA {} ApprovalRequest id={} has blank EntityId; using studentId from OriginalData ({})",
                        request.getRequestType(), request.getId(), orig.getStudentId());
                return orig.getStudentId();
            }
            Integer fromFlexibleOriginal = extractStudentIdFromJsonDocument(request.getOriginalData());
            if (fromFlexibleOriginal != null && fromFlexibleOriginal > 0) {
                logger.warn(
                        "STUDENT_DATA {} ApprovalRequest id={} has blank EntityId; parsing student id from OriginalData JSON ({})",
                        request.getRequestType(), request.getId(), fromFlexibleOriginal);
                return fromFlexibleOriginal;
            }
        }
        throw new IllegalArgumentException(
                "Cannot execute STUDENT_DATA " + request.getRequestType() + " for ApprovalRequest id=" + request.getId()
                        + ": EntityId is blank and neither RequestData nor OriginalData contains a usable studentId.");
    }

    /**
     * Frontend historically omitted {@code studentId} on edits; tolerate alternate JSON keys / nested payloads.
     */
    private Integer extractStudentIdFromJsonDocument(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            Integer id = extractStudentIdLoose(root);
            if (id != null && id > 0) {
                return id;
            }
            JsonNode envelope = root.get("studentData");
            if (envelope != null && envelope.isObject()) {
                id = extractStudentIdLoose(envelope);
                if (id != null && id > 0) {
                    return id;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Integer extractStudentIdLoose(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String[] keys = { "studentId", "student_id", "STUDENT_ID", "studentID" };
        for (String k : keys) {
            if (!node.has(k)) {
                continue;
            }
            JsonNode v = node.get(k);
            if (v == null || v.isNull()) {
                continue;
            }
            if (v.isNumber()) {
                int n = v.asInt();
                if (n > 0) {
                    return n;
                }
                continue;
            }
            if (v.isTextual()) {
                String s = v.asText().trim();
                if (!s.isEmpty()) {
                    try {
                        int n = Integer.parseInt(s);
                        if (n > 0) {
                            return n;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return null;
    }

    private void executeStudentFinanceRequest(ApprovalRequest request) throws Exception {
        StudentFinance entity = objectMapper.readValue(request.getRequestData(), StudentFinance.class);
        switch (request.getRequestType()) {
            case INSERT:
                Integer sid = resolveStudentFinanceStudentId(entity, request);
                if (sid == null || sid <= 0) {
                    throw new IllegalArgumentException(
                            "STUDENT_FINANCE INSERT requires a positive student id in request JSON (id/studentId) or ApprovalRequest.entityId");
                }
                StudentFinanceService.SeedAndMergeOutcome outcome =
                        studentFinanceService.seedFromStudentDataAndMergeDraft(sid, entity);
                int financeRows = outcome.newRowThisRun();
                StudentFinance persisted = outcome.merged();
                if (financeRows == 0) {
                    logger.warn(
                            "STUDENT_FINANCE row already existed or was not newly created this run (studentId={}); approval payload merged onto persisted row",
                            sid);
                }
                if (financeRows > 0) {
                    StudentFinancePayment pay = StudentFinanceLedgerMapper.paymentFromFinance(persisted, null);
                    if (pay != null) {
                        studentFinancePaymentRepository.save(pay);
                    }
                }
                /*
                 * The finance form no longer writes STUDENT_FINANCE_WAFAR itself, so the
                 * wafar row is materialized here — only once the request is approved.
                 * The note rides along on the payload; STUDENT_FINANCE has no column for it.
                 */
                studentFinanceWafarService.syncFromFinance(persisted, entity.getWafarNote());
                logger.info("Executed INSERT for student finance studentId={} (newRowThisRun={})", sid, financeRows);
                break;
            case UPDATE:
                Integer updateId = Integer.parseInt(request.getEntityId());
                StudentFinance updated = studentFinanceService.update(updateId, entity);
                studentFinanceWafarService.syncFromFinance(updated, entity.getWafarNote());
                logger.info("Executed UPDATE for student finance id: {}", updateId);
                break;
            case DELETE:
                Integer deleteId = Integer.parseInt(request.getEntityId());
                studentFinanceService.delete(deleteId);
                // The wafar row is meaningless without the finance record behind it.
                studentFinanceWafarService.deleteIfExists(deleteId);
                logger.info("Executed DELETE for student finance id: {}", deleteId);
                break;
        }
    }

    /**
     * Applies a wafar record edited directly on the wafar page, once approved.
     *
     * Each branch also writes through to STUDENT_FINANCE: a wafar the finance record does
     * not know about would leave the finance screen and every report showing an amount the
     * student never received. NET_AMOUNT is computed, so setting WAFAR_AMOUNT is enough.
     */
    private void executeStudentFinanceWafarRequest(ApprovalRequest request) throws Exception {
        StudentFinanceWafar entity = objectMapper.readValue(request.getRequestData(), StudentFinanceWafar.class);
        switch (request.getRequestType()) {
            case INSERT:
                /*
                 * STUDENT_ID is the manually-assigned primary key. A payload without it used
                 * to reach Hibernate and fail with "ids for this class must be manually
                 * assigned", which named neither the student nor the screen.
                 */
                if (entity.getId() == null || entity.getId() <= 0) {
                    entity.setId(parseEntityId(request));
                }
                studentFinanceWafarService.create(entity);
                studentFinanceWafarService.applyToFinance(entity);
                logger.info("Executed INSERT for student finance wafar studentId={}", entity.getId());
                break;
            case UPDATE:
                Integer updateId = Integer.parseInt(request.getEntityId());
                StudentFinanceWafar saved = studentFinanceWafarService.update(updateId, entity);
                studentFinanceWafarService.applyToFinance(saved);
                logger.info("Executed UPDATE for student finance wafar id: {}", updateId);
                break;
            case DELETE:
                Integer deleteId = Integer.parseInt(request.getEntityId());
                studentFinanceWafarService.deleteIfExists(deleteId);
                // Otherwise the finance record keeps deducting a wafar that no longer exists.
                studentFinanceWafarService.clearFinanceWafar(deleteId);
                logger.info("Executed DELETE for student finance wafar id: {}", deleteId);
                break;
        }
    }

    /** ApprovalRequest.entityId is the fallback when the wafar payload carries no student id. */
    private static Integer parseEntityId(ApprovalRequest request) {
        try {
            return Integer.parseInt(request.getEntityId());
        } catch (NumberFormatException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "STUDENT_FINANCE_WAFAR INSERT requires a student id in the request payload or entityId");
        }
    }

    /**
     * Chained STUDENT_FINANCE payloads may omit {@link StudentFinance#getId()} in JSON; ApprovalRequest.entityId is the fallback.
     */
    private static Integer resolveStudentFinanceStudentId(StudentFinance entity, ApprovalRequest request) {
        if (entity.getId() != null && entity.getId() > 0) {
            return entity.getId();
        }
        if (request.getEntityId() != null && !request.getEntityId().isBlank()) {
            try {
                int parsed = Integer.parseInt(request.getEntityId().trim());
                if (parsed > 0) {
                    entity.setId(parsed);
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /** Country for approval row: the study destination country (STUDY_COUNTRY_IN), consistent with scope filters. */
    private static Integer countryIdForStudentDataApprovalChain(StudentDatum h) {
        if (h == null) {
            return null;
        }
        return h.getStudyCountryIn();
    }

    /**
     * Safely resolve the study destination country for a student.
     * Returns null if studentId is null/zero or the student cannot be found.
     */
    /** For the N-04 ownership check on Wafar finance requests. */
    @org.springframework.beans.factory.annotation.Autowired
    private com.dilaw.mohe.ums.service.CountryScopeService countryScopeService;

    /**
     * The study country of the student a request is raised for, which becomes the
     * request's country.
     *
     * <p>Live QA N14 (26 Sep 2026): the 404 / 403 of {@code getStudentData} was caught
     * and null returned, and {@code createRequest} then filed the request under the
     * requester's own country. A fee, extension, travel, loan, allowance or received-by
     * request for another country's student was therefore raised, and approved at home.
     * The refusal is the answer now; only a request that names no student has no country.
     */
    private Integer resolveStudyCountryIn(Integer studentId) {
        if (studentId == null || studentId <= 0) {
            return null;
        }
        StudentDatum student = studentDataService.getStudentData(studentId);
        return student != null ? student.getStudyCountryIn() : null;
    }

    /**
     * Fills {@link StudentExtensionRequest#getStudentName()} from the student record so the
     * approval payload names who the extension is for. Best-effort: leaves it untouched on error.
     */
    private void resolveStudentNameInto(StudentExtensionRequest request) {
        if (request == null || request.getStudentName() != null && !request.getStudentName().isBlank()) {
            return;
        }
        request.setStudentName(resolveStudentName(request.getStudentId()));
    }

    /** Best-effort student name for a studentId; null on error or when not found. */
    private String resolveStudentName(Integer studentId) {
        if (studentId == null || studentId <= 0) {
            return null;
        }
        try {
            StudentDatum student = studentDataService.getStudentData(studentId);
            return student != null ? student.getStudentName() : null;
        } catch (Exception e) {
            logger.warn("Could not resolve studentName for studentId={}: {}", studentId, e.getMessage());
            return null;
        }
    }

    private void queueChainedStudentFinanceAfterCommit(String financeJson, long parentDataRequestId,
            Long chainRequestedBy, Integer chainCountryId, int studentIdForLog) {
        try {
            StudentFinance chainDraft = objectMapper.readValue(financeJson, StudentFinance.class);
            workflowService.createStudentFinanceApprovalChained(
                    RequestType.INSERT,
                    chainDraft,
                    null,
                    chainRequestedBy,
                    parentDataRequestId,
                    chainCountryId);
            logger.info("Queued chained STUDENT_FINANCE approval after COMMIT sid={}", studentIdForLog);
        } catch (Exception e) {
            logger.error(
                    "Chained STUDENT_FINANCE workflow failed AFTER COMMIT sid={}; student row exists — manual reconciliation may be needed: {}",
                    studentIdForLog, e.getMessage(), e);
        }
    }

    private void executeStudyUniversityFeeRequest(ApprovalRequest request) throws Exception {
        StudyUniversityFee entity = objectMapper.readValue(request.getRequestData(), StudyUniversityFee.class);
        switch (request.getRequestType()) {
            case INSERT:
                studyUniversityFeeService.create(entity);
                logger.info("Executed INSERT for study university fee");
                break;
            case UPDATE:
                Integer updateId = Integer.parseInt(request.getEntityId());
                studyUniversityFeeService.update(updateId, entity);
                logger.info("Executed UPDATE for study university fee id: {}", updateId);
                break;
            case DELETE:
                Integer deleteId = Integer.parseInt(request.getEntityId());
                studyUniversityFeeService.delete(deleteId);
                logger.info("Executed DELETE for study university fee id: {}", deleteId);
                break;
        }
    }

    private void executeStudentExtensionRequest(ApprovalRequest request) throws Exception {
        StudentExtensionRequest req = objectMapper.readValue(request.getRequestData(), StudentExtensionRequest.class);
        switch (request.getRequestType()) {
            case INSERT:
                studentExtensionService.create(req);
                logger.info("Executed INSERT for student extension");
                break;
            case UPDATE:
                Integer extensionId = Integer.parseInt(request.getEntityId());
                studentExtensionService.update(extensionId, req);
                logger.info("Executed UPDATE for student extension id: {}", extensionId);
                break;
            case DELETE:
                Integer deleteId = Integer.parseInt(request.getEntityId());
                studentExtensionService.delete(deleteId);
                logger.info("Executed DELETE for student extension id: {}", deleteId);
                break;
        }
    }

    private void executeStudentTravelTicketRequest(ApprovalRequest request) throws Exception {
        StudentTravelTicket ticket = objectMapper.readValue(request.getRequestData(), StudentTravelTicket.class);
        Integer actor = request.getRequestedBy() != null ? request.getRequestedBy().intValue() : null;
        // FIN-02: the Wafar draw-down records who approved the ticket, not who asked for it.
        Integer approver = request.getExecutedBy() != null ? request.getExecutedBy().intValue() : actor;
        switch (request.getRequestType()) {
            case INSERT: {
                StudentTravelTicket saved = studentTravelTicketService.create(ticket);
                ticket.setTravelTicketId(saved.getTravelTicketId());
                applyOrReverseTravelWafar(ticket, saved.getTravelTicketId(), actor, approver);
                logger.info("Executed INSERT for student travel ticket id: {}", saved.getTravelTicketId());
                break;
            }
            case UPDATE: {
                Integer updateId = Integer.parseInt(request.getEntityId());
                studentTravelTicketService.update(updateId, ticket);
                ticket.setTravelTicketId(updateId);
                applyOrReverseTravelWafar(ticket, updateId, actor, approver);
                logger.info("Executed UPDATE for student travel ticket id: {}", updateId);
                break;
            }
            case DELETE: {
                Integer deleteId = Integer.parseInt(request.getEntityId());
                studentTravelTicketService.delete(deleteId);
                travelTicketWafarService.reverseTravelTicketSpending(deleteId);
                logger.info("Executed DELETE for student travel ticket id: {}", deleteId);
                break;
            }
        }
    }

    /**
     * After a travel ticket is applied: draw the total from Wafar when the funding source is Wafar
     * (final balance guard + create/update the linked spending), otherwise release any prior spending.
     */
    private void applyOrReverseTravelWafar(StudentTravelTicket ticket, Integer ticketId, Integer actor, Integer approver) {
        if (travelTicketWafarService.isWafarSource(ticket.getFundingSourceId())) {
            java.math.BigDecimal total = TravelTicketWafarService.computeTotal(ticket);
            travelTicketWafarService.assertBalanceCovers(total, ticketId, ticket.getStudentId());
            travelTicketWafarService.applyTravelTicketSpending(ticket, total, actor, approver);
        } else {
            travelTicketWafarService.reverseTravelTicketSpending(ticketId);
        }
    }

    /**
     * Applies an approved student loan change.
     *
     * <p>The service methods are the ones the screen used to call directly, so
     * every rule they carry still runs — the identity id cleared before an
     * insert, {@code MIDATE} stamped, the country resolved from the student, the
     * amount checked against {@code CK_STUDENT_LOAN_AMOUNT_POSITIVE}. What moved
     * is when they run, not whether.
     */
    private void executeStudentLoanRequest(ApprovalRequest request) throws Exception {
        switch (request.getRequestType()) {
            case INSERT -> {
                StudentLoan entity = objectMapper.readValue(request.getRequestData(), StudentLoan.class);
                StudentLoan saved = studentLoanService.create(entity);
                logger.info("Executed INSERT for student loan id: {}", saved.getLoanId());
            }
            case UPDATE -> {
                Integer id = Integer.parseInt(request.getEntityId());
                studentLoanService.update(id, objectMapper.readTree(request.getRequestData()));
                logger.info("Executed UPDATE for student loan id: {}", id);
            }
            case DELETE -> {
                Integer id = Integer.parseInt(request.getEntityId());
                studentLoanService.delete(id);
                logger.info("Executed DELETE for student loan id: {}", id);
            }
        }
    }

    /** Applies an approved books allowance change — see {@link #executeStudentLoanRequest}. */
    private void executeStudentBooksAllowanceRequest(ApprovalRequest request) throws Exception {
        switch (request.getRequestType()) {
            case INSERT -> {
                StudentBooksAllowance entity =
                        objectMapper.readValue(request.getRequestData(), StudentBooksAllowance.class);
                StudentBooksAllowance saved = studentBooksAllowanceService.create(entity);
                logger.info("Executed INSERT for books allowance id: {}", saved.getBooksAllowId());
            }
            case UPDATE -> {
                Integer id = Integer.parseInt(request.getEntityId());
                studentBooksAllowanceService.update(id, objectMapper.readTree(request.getRequestData()));
                logger.info("Executed UPDATE for books allowance id: {}", id);
            }
            case DELETE -> {
                Integer id = Integer.parseInt(request.getEntityId());
                studentBooksAllowanceService.delete(id);
                logger.info("Executed DELETE for books allowance id: {}", id);
            }
        }
    }

    /**
     * Applies an approved finance-recipient change.
     *
     * <p>The recipient's documents are not part of this: they are uploaded and
     * removed immediately, and a delete here removes the row through the same
     * service the controller used to call.
     */
    private void executeStudyFinReceivedWhomRequest(ApprovalRequest request) throws Exception {
        switch (request.getRequestType()) {
            case INSERT -> {
                StudyFinReceivedWhom entity =
                        objectMapper.readValue(request.getRequestData(), StudyFinReceivedWhom.class);
                StudyFinReceivedWhom saved = studyFinReceivedWhomService.create(entity);
                logger.info("Executed INSERT for finance recipient id: {}", saved.getId());
            }
            case UPDATE -> {
                Integer id = Integer.parseInt(request.getEntityId());
                studyFinReceivedWhomService.update(id, objectMapper.readTree(request.getRequestData()));
                logger.info("Executed UPDATE for finance recipient id: {}", id);
            }
            case DELETE -> {
                Integer id = Integer.parseInt(request.getEntityId());
                studyFinReceivedWhomService.delete(id);
                logger.info("Executed DELETE for finance recipient id: {}", id);
                withdrawFinanceLinksTo(id, request.getId(),
                        request.getExecutedBy() != null ? request.getExecutedBy() : request.getRequestedBy());
            }
        }
    }

    /**
     * A finance record's "received by" link is itself an approval request
     * ({@code PATCH /studentFinance/{id}/receivedWhom}). When the recipient it
     * points at is deleted, a link still waiting for a decision would, once
     * approved, write the id of a row that no longer exists — and until then it
     * sits in the queue pointing at nothing (live QA L8, 24 Sep 2026: request
     * 21718 for recipient 49). Such links are rejected here, the deletion named
     * as the reason. Only the requests that <em>introduce</em> the link are
     * touched: a pending edit of a record already linked to the recipient is
     * someone else's change and stays. A withdrawal that fails is logged and
     * does not undo the delete.
     */
    private void withdrawFinanceLinksTo(Integer whomId, Long deleteRequestId, Long deletedBy) {
        for (ApprovalRequest pending : workflowService.getPendingByEntityType("STUDENT_FINANCE")) {
            try {
                if (!introducesLinkTo(pending, whomId)) continue;
                // FIN-11: rejectObsolete, not rejectRequest. The latter checks the
                // signed-in approver's right to reject this finance request, and its
                // refusal, though caught here, marked the shared transaction
                // rollback-only, so the approved delete itself was lost.
                workflowService.rejectObsolete(pending.getId(), deletedBy,
                        "Recipient " + whomId + " was deleted (approval request " + deleteRequestId + ")");
                logger.info("Withdrew finance link request {} after deleting recipient {}", pending.getId(), whomId);
            } catch (Exception e) {
                logger.warn("Could not withdraw finance link request {} after deleting recipient {}: {}",
                        pending.getId(), whomId, e.getMessage());
            }
        }
    }

    /** The proposal names {@code whomId} as the recipient and the record on file did not. */
    boolean introducesLinkTo(ApprovalRequest pending, Integer whomId) throws Exception {
        if (pending.getRequestData() == null) return false;
        com.fasterxml.jackson.databind.JsonNode proposed = objectMapper.readTree(pending.getRequestData());
        if (!proposed.path("studyReceivedWhomId").isIntegralNumber()
                || proposed.path("studyReceivedWhomId").asInt() != whomId) {
            return false;
        }
        if (pending.getOriginalData() == null) return true;
        com.fasterxml.jackson.databind.JsonNode original = objectMapper.readTree(pending.getOriginalData());
        return !original.path("studyReceivedWhomId").isIntegralNumber()
                || original.path("studyReceivedWhomId").asInt() != whomId;
    }

    /**
     * Check if approval is required for an entity type.
     */
    public boolean isApprovalRequired(String entityType) {
        return true;
    }

    /**
     * Get a DTO response indicating changes are pending approval.
     */
    public Map<String, Object> createPendingResponse(ApprovalRequest request) {
        return Map.of(
                "status", "PENDING_APPROVAL",
                "message", "Your changes have been submitted for approval",
                "requestId", request.getId(),
                "currentLevel", request.getCurrentLevel(),
                "totalLevels", request.getConfig().getTotalLevels());
    }

    /**
     * Raises a removal request for one student document.
     *
     * Keyed on the document id so two removals of the same document cannot queue at once,
     * while different documents of the same student stay independent.
     */
    @Transactional
    public ApprovalRequest createDocumentDeleteRequest(Integer documentId, String documentSnapshot,
            Integer countryId, Long requestedBy) {
        return workflowService.createRequest(
                "STUDENT_DOCUMENT",
                RequestType.DELETE,
                String.valueOf(documentId),
                documentSnapshot,
                documentSnapshot,
                requestedBy,
                countryId);
    }

    /** Applies an approved document removal. */
    private void executeStudentDocumentRequest(ApprovalRequest request) {
        Integer documentId = Integer.valueOf(request.getEntityId());
        switch (request.getRequestType()) {
            case DELETE -> {
                studentDocumentRemovalService.remove(documentId, request.getRequestedBy());
                logger.info("Executed document removal id={} (approvalRequest id={})",
                        documentId, request.getId());
            }
            default -> logger.warn("Unsupported STUDENT_DOCUMENT request type {} on request {}",
                    request.getRequestType(), request.getId());
        }
    }
}
