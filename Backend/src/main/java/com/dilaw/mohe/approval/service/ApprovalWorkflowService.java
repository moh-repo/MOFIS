package com.dilaw.mohe.approval.service;

import com.dilaw.mohe.codes.ApprovalAction;
import com.dilaw.mohe.approval.event.ApprovalExecutionEvent;
import com.dilaw.mohe.approval.event.ApprovalRequestCreatedEvent;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.approval.model.*;
import com.dilaw.mohe.approval.repository.*;
import com.dilaw.mohe.ums.model.UmsRole;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.UmsUserService;
import com.dilaw.mohe.security.policy.AccessControl;
import com.dilaw.mohe.security.policy.AccessSubject;
import com.dilaw.mohe.security.policy.ApprovalDecisionContext;
import com.dilaw.mohe.security.policy.ApprovalRequestPolicy;
import com.dilaw.mohe.security.policy.Decision;
import com.dilaw.mohe.config.tenant.WafarWriteKey;
import org.springframework.data.domain.PageImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import com.dilaw.mohe.approval.event.ApprovalRequestDecidedEvent;

/**
 * Core service for managing approval workflow.
 * Handles request creation, approval, rejection, and execution.
 */
@Service
public class ApprovalWorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalWorkflowService.class);

    @Autowired
    private ApprovalConfigRepository configRepository;

    @Autowired
    private ApprovalLevelRepository levelRepository;

    @Autowired
    private ApprovalRequestRepository requestRepository;

    @Autowired
    private ApprovalStepRepository stepRepository;

    @Autowired
    private ApprovalHistoryRepository historyRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private UmsUserService userService;

    @Autowired
    private CountryScopeService countryScopeService;

    /** Records every use of the super-admin bypasses in {@link #approveStep} (O-01, O-02). */
    @Autowired
    private com.dilaw.mohe.ums.service.UmsAuditLogService auditLogService;

    /** R-16: who may sign a step is {@link ApprovalRequestPolicy}; this class audits its exemptions. */
    @Autowired
    private AccessControl accessControl;

    /** N-04.d: raised when the super admin signs a Wafar request, so the execution can write the rows. */
    @Autowired
    private WafarWriteKey wafarWriteKey;

    /**
     * Create a new approval request with all required steps based on entity type
     * config.
     */
    @Transactional
    public ApprovalRequest createRequest(String entityType, RequestType requestType,
            String entityId, String requestData,
            String originalData, Long requestedBy) {
        return createRequest(entityType, requestType, entityId, requestData, originalData, requestedBy, null);
    }

    @Transactional
    public ApprovalRequest createRequest(String entityType, RequestType requestType,
            String entityId, String requestData,
            String originalData, Long requestedBy, Integer countryId) {
        return createRequest(entityType, requestType, entityId, requestData, originalData, requestedBy, countryId, null);
    }

    /** Full create with optional chain to parent STUDENT_DATA request. Must be invoked through Spring proxy for REQUIRES_NEW. */
    @Transactional
    public ApprovalRequest createRequest(String entityType, RequestType requestType,
            String entityId, String requestData,
            String originalData, Long requestedBy, Integer countryId, Long parentApprovalRequestId) {
        return createRequestInner(entityType, requestType, entityId, requestData, originalData, requestedBy, countryId, parentApprovalRequestId);
    }

    /**
     * Commit chained finance approval AFTER parent STUDENT_DATA txn commits — avoids SQL Server FK lock wait
     * (child insert referencing parent ApprovalRequests row still locked by EXECUTE txn).
     */
    @Transactional
    public ApprovalRequest createStudentFinanceApprovalChained(RequestType requestType,
            StudentFinance proposedData,
            StudentFinance originalData,
            Long requestedBy,
            Long parentStudentDataRequestId,
            Integer countryId) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        String entityId = originalData != null ? String.valueOf(originalData.getId())
                : (proposedData.getId() != null ? String.valueOf(proposedData.getId()) : null);
        String requestData = mapper.writeValueAsString(proposedData);
        String origData = originalData != null ? mapper.writeValueAsString(originalData) : null;

        ApprovalConfig config = configRepository.findByEntityTypeAndIsActiveTrue("STUDENT_FINANCE")
                .orElseThrow(() -> new RuntimeException(
                        "No approval config found for entity type STUDENT_FINANCE"));

        List<ApprovalRequest> existingRequests = requestRepository.findByEntityTypeAndEntityIdAndStatusIn(
                "STUDENT_FINANCE",
                entityId,
                List.of(ApprovalStatus.PENDING, ApprovalStatus.IN_PROGRESS));

        if (parentStudentDataRequestId != null) {
            Optional<ApprovalRequest> sameChain = existingRequests.stream()
                    .filter(r -> parentStudentDataRequestId.equals(r.getParentApprovalRequestId()))
                    .findFirst();
            if (sameChain.isPresent()) {
                ApprovalRequest reused = sameChain.get();
                logger.info("Reusing chained STUDENT_FINANCE approval id={} for parent STUDENT_DATA id={}",
                        reused.getId(), parentStudentDataRequestId);
                return reused;
            }
            for (ApprovalRequest r : existingRequests) {
                Long p = r.getParentApprovalRequestId();
                if (p != null && !Objects.equals(parentStudentDataRequestId, p)) {
                    throw new IllegalStateException(
                            "Student " + entityId
                                    + " already has pending STUDENT_FINANCE linked to another onboarding request.");
                }
            }
            for (ApprovalRequest r : existingRequests) {
                if (r.getParentApprovalRequestId() == null) {
                    rejectStaleFinancePendingWithoutParent(r, requestedBy,
                            "Superseded by chained student onboarding (parent request " + parentStudentDataRequestId
                                    + ")");
                }
            }
            existingRequests = requestRepository.findByEntityTypeAndEntityIdAndStatusIn(
                    "STUDENT_FINANCE",
                    entityId,
                    List.of(ApprovalStatus.PENDING, ApprovalStatus.IN_PROGRESS));
        }

        if (!existingRequests.isEmpty()) {
            throw new PendingApprovalExistsException(existingRequests.get(0).getId());
        }

        ApprovalRequest request = new ApprovalRequest();
        request.setConfig(config);
        request.setRequestType(requestType);
        request.setEntityType("STUDENT_FINANCE");
        request.setEntityId(entityId);
        request.setRequestData(requestData);
        request.setOriginalData(origData);
        request.setRequestedBy(requestedBy);
        request.setStatus(ApprovalStatus.PENDING);
        request.setCurrentLevel(1);
        // Same fallback as createRequestInner: a request with no country is
        // ministry-only, so leaving one null would hide it from the officer who
        // raised it.
        request.setCountryId(countryId != null ? countryId : countryOfRequester());
        request.setParentApprovalRequestId(parentStudentDataRequestId);

        request = requestRepository.save(request);

        List<ApprovalLevel> levels = levelRepository.findByConfigIdAndIsActiveTrueOrderByLevelOrderAsc(config.getId());
        List<ApprovalStep> steps = new ArrayList<>();

        for (ApprovalLevel level : levels) {
            ApprovalStep step = new ApprovalStep();
            step.setRequest(request);
            step.setLevelOrder(level.getLevelOrder());
            step.setLevel(level);
            step.setStatus(ApprovalStatus.PENDING);
            steps.add(step);
        }

        stepRepository.saveAll(steps);
        request.setSteps(steps);
        request.setCurrentLevel(firstLevelOf(levels)); // FIN-06

        recordHistory(request, ApprovalAction.CREATED, requestedBy, null,
                null, ApprovalStatus.PENDING.name(),
                "Request created for " + requestType.name() + " on STUDENT_FINANCE (chained)");
        eventPublisher.publishEvent(
                new ApprovalRequestCreatedEvent(request.getId(), "STUDENT_FINANCE", requestType));

        return request;
    }

    /**
     * FIN-11: rejects a still-open request that an approved change elsewhere has made
     * obsolete, as bookkeeping of that change rather than as anyone's decision on it.
     *
     * <p>It joins the caller's transaction and, unlike {@link #rejectRequest}, has no
     * refusal to throw: no policy check (the change that obsoletes the request was
     * itself approved) and a request that is no longer open is left alone. That matters
     * because a runtime exception thrown through a transactional proxy marks the
     * shared transaction rollback-only even when the caller catches it — the approved
     * recipient DELETE was lost with {@code UnexpectedRollbackException} when the
     * approver lacked the right to reject the finance request it withdrew.
     *
     * @return whether the request was open and is now rejected
     */
    @Transactional
    public boolean rejectObsolete(Long requestId, Long rejectedBy, String reason) {
        ApprovalRequest r = requestRepository.findById(requestId).orElse(null);
        if (r == null || (r.getStatus() != ApprovalStatus.PENDING && r.getStatus() != ApprovalStatus.IN_PROGRESS)) {
            return false;
        }
        rejectStaleFinancePendingWithoutParent(r, rejectedBy, reason);
        return true;
    }

    /**
     * Marks a pending/in-progress finance workflow as rejected so onboarding can chain a new STUDENT_FINANCE row.
     */
    private void rejectStaleFinancePendingWithoutParent(ApprovalRequest r, Long rejectedBy, String reason) {
        String truncReason = reason.length() > 500 ? reason.substring(0, 497) + "..." : reason;
        String oldStatus = r.getStatus().name();
        r.setStatus(ApprovalStatus.REJECTED);
        r.setRejectionReason(truncReason);
        r.setRejectedBy(rejectedBy);
        r.setRejectedAt(Instant.now());
        requestRepository.save(r);
        List<ApprovalStep> steps = stepRepository.findByRequestIdOrderByLevelOrderAsc(r.getId());
        for (ApprovalStep s : steps) {
            ApprovalStatus st = s.getStatus();
            if (st != ApprovalStatus.PENDING && st != ApprovalStatus.IN_PROGRESS) {
                continue;
            }
            s.setStatus(ApprovalStatus.REJECTED);
            s.setApprovedBy(rejectedBy);
            s.setApprovedAt(Instant.now());
            s.setComments(truncReason);
        }
        stepRepository.saveAll(steps);
        recordHistory(r, ApprovalAction.REJECTED, rejectedBy, r.getCurrentLevel(),
                oldStatus, ApprovalStatus.REJECTED.name(), reason);
        eventPublisher.publishEvent(new ApprovalRequestDecidedEvent(r.getId(), ApprovalStatus.REJECTED));
        logger.warn("Rejected orphan STUDENT_FINANCE approval request id={}: {}", r.getId(), reason);
    }

    private ApprovalRequest createRequestInner(String entityType, RequestType requestType,
            String entityId, String requestData,
            String originalData, Long requestedBy, Integer countryId, Long parentApprovalRequestId) {

        // Get config for entity type (fallback to DEFAULT if not found)
        ApprovalConfig config = configRepository.findByEntityTypeAndIsActiveTrue(entityType)
                .orElseGet(() -> configRepository.findByEntityTypeAndIsActiveTrue("DEFAULT")
                        .orElseThrow(
                                () -> new RuntimeException("No approval config found for entity type: " + entityType)));

        /*
         * INSERT rows without a persisted entity PK use entityId=null. Searching pending
         * requests where entity_id IS NULL would match unrelated inserts — e.g. every
         * new STUDY_UNIVERSITY_FEE would block the next until approval completes.
         * Skip duplicate-request check here; downstream execution still validates (e.g. fee duplicate periods).
         */
        boolean skipDupCheck =
                entityId == null
                        && requestType == RequestType.INSERT
                        && ("STUDENT_DATA".equals(entityType)
                                || "STUDY_UNIVERSITY_FEE".equals(entityType)
                                || "STUDENT_EXTENSION".equals(entityType)
                                || "STUDENT_TRAVEL_TICKET".equals(entityType)
                                // Same reason: two new loans, two new books allowances or
                                // two new recipients both carry entityId=null, and without
                                // this the second Add is refused as a duplicate of the first.
                                || "STUDENT_LOAN".equals(entityType)
                                || "STUDENT_BOOKS_ALLOWANCE".equals(entityType)
                                || "STUDY_FIN_RECEIVED_WHOM".equals(entityType));

        if (!skipDupCheck) {
            // Check if there's already a pending request for the same entity
            List<ApprovalRequest> existingRequests = requestRepository.findByEntityTypeAndEntityIdAndStatusIn(
                    entityType, entityId,
                    List.of(ApprovalStatus.PENDING, ApprovalStatus.IN_PROGRESS));
            if (!existingRequests.isEmpty()) {
                throw new PendingApprovalExistsException(existingRequests.get(0).getId());
            }
        }

        // Create the request
        ApprovalRequest request = new ApprovalRequest();
        request.setConfig(config);
        request.setRequestType(requestType);
        request.setEntityType(entityType);
        request.setEntityId(entityId);
        request.setRequestData(requestData);
        request.setOriginalData(originalData);
        request.setRequestedBy(requestedBy);
        request.setStatus(ApprovalStatus.PENDING);
        request.setCurrentLevel(1);
        request.setCountryId(countryId != null ? countryId : countryOfRequester());
        request.setParentApprovalRequestId(parentApprovalRequestId);

        request = requestRepository.save(request);

        // Get levels and create steps for each level
        List<ApprovalLevel> levels = levelRepository.findByConfigIdAndIsActiveTrueOrderByLevelOrderAsc(config.getId());
        List<ApprovalStep> steps = new ArrayList<>();

        for (ApprovalLevel level : levels) {
            ApprovalStep step = new ApprovalStep();
            step.setRequest(request);
            step.setLevelOrder(level.getLevelOrder());
            step.setLevel(level);
            step.setStatus(ApprovalStatus.PENDING);
            steps.add(step);
        }

        stepRepository.saveAll(steps);
        request.setSteps(steps);
        request.setCurrentLevel(firstLevelOf(levels)); // FIN-06

        // Record history
        recordHistory(request, ApprovalAction.CREATED, requestedBy, null,
                null, ApprovalStatus.PENDING.name(),
                "Request created for " + requestType.name() + " on " + entityType);
        // H2: announced in the system notices once this transaction commits.
        eventPublisher.publishEvent(new ApprovalRequestCreatedEvent(request.getId(), entityType, requestType));

        return request;
    }

    /**
     * For UPDATE operations: if a PENDING request already exists for this entity, overwrite its data
     * (the approver will see the latest version). If the existing request is IN_PROGRESS (already being
     * reviewed), throw so the user knows to wait. If none exists, create normally.
     */
    @Transactional
    public ApprovalRequest createOrReplacePendingRequest(String entityType, RequestType requestType,
            String entityId, String requestData, String originalData,
            Long requestedBy, Integer countryId) {

        List<ApprovalRequest> existing = requestRepository.findByEntityTypeAndEntityIdAndStatusIn(
                entityType, entityId,
                List.of(ApprovalStatus.PENDING, ApprovalStatus.IN_PROGRESS));

        if (!existing.isEmpty()) {
            ApprovalRequest req = existing.get(0);
            if (req.getStatus() == ApprovalStatus.IN_PROGRESS) {
                throw new IllegalStateException(
                        "This record is currently under review and cannot be modified until the review is complete");
            }
            // Overwrite the PENDING request with the freshest data
            req.setRequestData(requestData);
            req.setOriginalData(originalData);
            req.setRequestedBy(requestedBy);
            recordHistory(req, ApprovalAction.UPDATED, requestedBy, req.getCurrentLevel(),
                    ApprovalStatus.PENDING.name(), ApprovalStatus.PENDING.name(),
                    "Request data updated before approval");
            return requestRepository.save(req);
        }

        return createRequestInner(entityType, requestType, entityId, requestData,
                originalData, requestedBy, countryId, null);
    }

    /**
     * Approve the current level of a request.
     *
     * <p>The authenticated user ID from JWT (Spring Security principal) is used for role checks and auditing.
     * The client-sent {@code approverId} is ignored so misconfigured UI fallbacks cannot cause false
     * "User does not have the required role" errors.</p>
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public ApprovalRequest approveStep(Long requestId, Long ignoredClientApproverId, String comments) {
        Long approverId = SecurityRequestUtils.currentUserId()
                .orElseThrow(() -> new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException(
                        "Not authenticated — sign in with UMS/JWT before approving"));

        ApprovalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Request not found: " + requestId));
        ApprovalStep currentStep = stepRepository.findByRequestIdAndLevelOrder(requestId, request.getCurrentLevel())
                .orElse(null);
        List<ApprovalStep> allSteps = stepRepository.findByRequestIdOrderByLevelOrderAsc(requestId);

        // R-16 (6 Sep 2026): one policy answers the country scope, requester ≠
        // approver (O-01), Wafar ownership (N-04), the required role (O-02) and the
        // position or named approvers (N-03.c). The super admin's exemptions come
        // back on the decision and are audited here, one row each, as before.
        Decision decision = accessControl.assertAllowed(
                new ApprovalDecisionContext(request, currentStep == null ? null : currentStep.getLevel(),
                        priorApprovers(allSteps, request)),
                "APPROVE");
        if (request.getStatus() == ApprovalStatus.REJECTED ||
                request.getStatus() == ApprovalStatus.EXECUTED) {
            throw new IllegalStateException("Request is already " + request.getStatus().name());
        }
        if (currentStep == null) {
            throw new IllegalStateException("Current step not found");
        }
        auditExemptions(decision, approverId, request, currentStep.getLevel());

        String oldStatus = request.getStatus().name();

        // Mark step as approved
        currentStep.setStatus(ApprovalStatus.APPROVED);
        currentStep.setApprovedBy(approverId);
        currentStep.setApprovedAt(Instant.now());
        currentStep.setComments(comments);
        stepRepository.save(currentStep);

        // FIN-06: the chain is the request's own steps, fixed when it was raised. The
        // final level used to be the live config's totalLevels, which the config
        // update accepted from the request body, so setting it to 1 made every
        // in-flight three-level request final after one signature. And "next" is the
        // next step on file, not currentLevel + 1: a level that was inactive when the
        // request was raised has no step, and waiting for it left the request stuck
        // on "Current step not found".
        Integer nextLevel = nextLevelAfter(allSteps, request.getCurrentLevel());
        if (nextLevel == null) {
            // Final approval - execute the request
            request.setStatus(ApprovalStatus.APPROVED);
            recordHistory(request, ApprovalAction.APPROVED, approverId, request.getCurrentLevel(),
                    oldStatus, ApprovalStatus.APPROVED.name(),
                    "Final approval by level " + request.getCurrentLevel() + ". " + (comments != null ? comments : ""));

            // Auto-execute after final approval
            return executeRequest(request, approverId);
        } else {
            // Move to next level
            Integer approvedLevel = request.getCurrentLevel();
            request.setCurrentLevel(nextLevel);
            request.setStatus(ApprovalStatus.IN_PROGRESS);
            requestRepository.save(request);

            recordHistory(request, ApprovalAction.APPROVED, approverId, approvedLevel,
                    oldStatus, ApprovalStatus.IN_PROGRESS.name(),
                    "Level " + approvedLevel + " approved. Moving to level "
                            + request.getCurrentLevel() + ". " + (comments != null ? comments : ""));
        }

        return reloadForResponse(requestId);
    }

    /**
     * Reject a request at any level. Uses JWT user ID for auditing (client {@code rejectedBy} is ignored).
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public ApprovalRequest rejectRequest(Long requestId, Long ignoredClientRejectedBy, String reason) {
        Long rejectedBy = SecurityRequestUtils.currentUserId()
                .orElseThrow(() -> new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException(
                        "Not authenticated — sign in with UMS/JWT before rejecting"));

        ApprovalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Request not found: " + requestId));
        // Live QA H5: the country scope, then the requester withdrawing, a holder of
        // APP_PENDING.REJECT, or someone who may sign the waiting level (see the policy).
        ApprovalStep currentStep = stepRepository.findByRequestIdAndLevelOrder(requestId, request.getCurrentLevel())
                .orElse(null);
        accessControl.assertAllowed(
                new ApprovalDecisionContext(request, currentStep == null ? null : currentStep.getLevel()), "REJECT");
        if (request.getStatus() == ApprovalStatus.REJECTED ||
                request.getStatus() == ApprovalStatus.EXECUTED) {
            throw new IllegalStateException("Request is already " + request.getStatus().name());
        }

        String oldStatus = request.getStatus().name();

        // Mark current step as rejected
        if (currentStep != null) {
            currentStep.setStatus(ApprovalStatus.REJECTED);
            currentStep.setApprovedBy(rejectedBy);
            currentStep.setApprovedAt(Instant.now());
            currentStep.setComments(reason);
            stepRepository.save(currentStep);
        }

        // Update request
        request.setStatus(ApprovalStatus.REJECTED);
        request.setRejectionReason(reason);
        request.setRejectedBy(rejectedBy);
        request.setRejectedAt(Instant.now());
        requestRepository.save(request);

        recordHistory(request, ApprovalAction.REJECTED, rejectedBy, request.getCurrentLevel(),
                oldStatus, ApprovalStatus.REJECTED.name(),
                "Rejected at level " + request.getCurrentLevel() + ". Reason: " + reason);
        eventPublisher.publishEvent(new ApprovalRequestDecidedEvent(request.getId(), ApprovalStatus.REJECTED));

        return reloadForResponse(requestId);
    }

    /**
     * The requester takes back their own request (round-5 QA R5-16, decision D-4). The
     * policy always let the requester reject, but the reject endpoint sits behind the
     * approvals permission, so a Finance Officer could raise a request and never take it
     * back. Only the requester passes; the request is then rejected as usual, with a
     * reason that says it was withdrawn, so history and notifications stay as they are.
     * Someone else's request answers 404, as {@link #getRequestById} does, so the
     * endpoint cannot be used to walk request ids.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public ApprovalRequest withdrawRequest(Long requestId, String reason) {
        requestRepository.findById(requestId)
                .filter(r -> accessControl.currentSubject().is(r.getRequestedBy()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Request not found: " + requestId));
        String note = reason == null || reason.isBlank() ? "" : ": " + reason.trim();
        return rejectRequest(requestId, null, "Withdrawn by the requester" + note);
    }

    /**
     * The codes of {@code MoheSecurityExpressions.APPROVALS}, the approvals pages' read
     * gate. ApprovalWithdrawTest keeps the two in step.
     */
    static final List<String> APPROVALS_READ_CODES =
            List.of("APPROVALS.VIEW", "APP_PENDING.VIEW", "APP_PENDING.APPROVE", "APP_PENDING.REJECT", "UMS_ADMIN.VIEW");

    /**
     * One request for its change view and staged files (round-5 QA R5-16): what
     * {@link #getRequestById} gives an approvals reader, and to anyone else only a
     * request they raised themselves. My requests is open to requesters who hold no
     * approvals permission, and its details dialog shows what the request changes.
     */
    public Optional<ApprovalRequest> getRequestForViewer(Long id) {
        AccessSubject me = accessControl.currentSubject();
        boolean reader = me.isSuperAdmin() || APPROVALS_READ_CODES.stream().anyMatch(me::hasCode);
        return getRequestById(id).filter(r -> reader || me.is(r.getRequestedBy()));
    }

    /**
     * Execute an approved request (apply the changes).
     * This method should be extended to actually apply entity changes.
     */
    @Transactional
    public ApprovalRequest executeRequest(ApprovalRequest request, Long executedBy) {
        if (request.getStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalStateException("Request must be fully approved before execution");
        }

        String oldStatus = request.getStatus().name();

        request.setStatus(ApprovalStatus.EXECUTED);
        request.setExecutedAt(Instant.now());
        request.setExecutedBy(executedBy);
        requestRepository.save(request);

        recordHistory(request, ApprovalAction.EXECUTED, executedBy, null,
                oldStatus, ApprovalStatus.EXECUTED.name(),
                "Request executed. Changes applied to " + request.getEntityType());

        // Publish event for entity-specific execution
        eventPublisher.publishEvent(new ApprovalExecutionEvent(this, request, executedBy));
        eventPublisher.publishEvent(new ApprovalRequestDecidedEvent(request.getId(), ApprovalStatus.EXECUTED));

        return reloadForResponse(request.getId());
    }

    /**
     * Get pending requests for a user based on their roles.
     */
    public List<ApprovalRequest> getPendingForRoles(List<Long> roleIds) {
        return requestRepository.findPendingForRoles(roleIds);
    }

    /**
     * Get requests submitted by a user.
     *
     * <p>Routed through the paged, scoped variant rather than the repository's
     * derived finder. An unscoped {@code getMyRequests} sitting next to a scoped
     * one is a trap: the next caller picks whichever name reads best, and only one
     * of them is safe.
     */
    public List<ApprovalRequest> getMyRequests(Long userId) {
        return getMyRequests(userId, PageRequest.of(0, Integer.MAX_VALUE)).getContent();
    }

    public Page<ApprovalRequest> getMyRequests(Long userId, Pageable pageable) {
        return getMyRequests(userId, pageable, null, null, null);
    }

    /**
     * The caller's own requests, and only the ones in their current country scope.
     *
     * <p>Identity alone is not scope. "My requests" and "requests my attaché may
     * see" are different sets the moment an officer is reassigned, and the requests
     * they raised at the previous attaché carry that attaché's student data. The
     * consequence to be aware of: after a reassignment those requests disappear
     * from this screen, and the officer at the attaché that owns them is the one
     * who can still see them.
     */
    public Page<ApprovalRequest> getMyRequests(Long userId, Pageable pageable,
            RequestType requestType, String entityType, ApprovalStatus status) {
        CountryScopeService.CountryScope scope = countryScopeService.getScope();
        if (!scope.isMinistryUser() && scope.getAllowedCountryIds().isEmpty()) {
            return Page.empty(pageable);
        }
        boolean unscoped = scope.isMinistryUser();
        // Never empty, and ignored entirely when unscoped.
        List<Integer> countryIds = unscoped ? List.of(-1) : scope.getAllowedCountryIds();

        Page<ApprovalRequest> page = requestRepository.findByRequestedBy(
                userId, pageable, requestType, entityType, status, unscoped, countryIds);
        populateRequesterNames(page);
        return page;
    }

    /**
     * Get all pending requests (scoped by current user's country access).
     */
    public List<ApprovalRequest> getAllPending() {
        return getAllPending(PageRequest.of(0, Integer.MAX_VALUE)).getContent();
    }

    public Page<ApprovalRequest> getAllPending(Pageable pageable) {
        return getAllPending(pageable, null, null);
    }

    public Page<ApprovalRequest> getAllPending(Pageable pageable, RequestType requestType, String entityType) {
        CountryScopeService.CountryScope scope = countryScopeService.getScope();
        Page<ApprovalRequest> page;
        if (scope.isMinistryUser()) {
            page = requestRepository.findAllPending(pageable, requestType, entityType);
        } else {
            List<Integer> ids = scope.getAllowedCountryIds();
            if (ids.isEmpty()) return Page.empty(pageable);
            page = requestRepository.findAllPendingWithCountryFilter(pageable, ids, requestType, entityType);
        }
        populateRequesterNames(page);
        return page;
    }

    /*
     * ── Country scope for the detail paths ────────────────────────────────────
     *
     * The list endpoints (/pending, /completed, and the unfiltered list) have been
     * scoped for a while. The methods that fetch one request, or the requests for
     * one entity, were not — and they carry the same RequestData, holding whatever
     * the student's record was about to become. Reading another attaché's request
     * needed only its id.
     *
     * A NULL country is ministry-only, consistent with the rest of this change.
     * V046 filled the nine rows that had one, and createRequestInner now falls
     * back to the requester's own attaché, so a new NULL means the request truly
     * belongs to no country.
     */

    /** Whether this request is inside the caller's scope. Null country = ministry only. */
    private boolean inScope(ApprovalRequest request) {
        if (request == null) return false;
        if (countryScopeService.canAccessAllCountries()) return true;
        Integer country = request.getCountryId();
        return country != null && countryScopeService.canAccessCountry(country);
    }

    private List<ApprovalRequest> onlyInScope(List<ApprovalRequest> requests) {
        if (countryScopeService.canAccessAllCountries()) return requests;
        return requests.stream().filter(this::inScope).collect(Collectors.toList());
    }

    /**
     * The country to stamp when a caller does not supply one. Their own, when they
     * have exactly one — which is every country officer. A ministry user creating
     * a request with no country leaves it null, and it stays ministry-only.
     */
    private Integer countryOfRequester() {
        CountryScopeService.CountryScope scope = countryScopeService.getScope();
        List<Integer> allowed = scope.getAllowedCountryIds();
        return (!scope.isMinistryUser() && allowed.size() == 1) ? allowed.get(0) : null;
    }

    /**
     * One request by id — empty rather than another country's row.
     *
     * <p>Empty, not forbidden: the controller turns it into a 404, and a 403 would
     * confirm the request exists, which is all that is needed to walk the ids.
     */
    public Optional<ApprovalRequest> getRequestById(Long id) {
        return requestRepository.findByIdWithConfig(id).filter(this::inScope);
    }

    /**
     * Requests for one entity that have not been decided yet.
     *
     * APPROVED counts as undecided here because execution happens after approval — the
     * record still differs from what the viewer sees until it runs.
     */
    /** Every request ever raised for one record, newest first. */
    public List<ApprovalRequest> getAllForEntity(String entityType, String entityId) {
        return onlyInScope(
                requestRepository.findByEntityTypeAndEntityIdOrderByRequestedAtDesc(entityType, entityId));
    }

    /** Every request of one entity type, for callers that must resolve the target themselves. */
    public List<ApprovalRequest> getAllByEntityType(String entityType) {
        return onlyInScope(requestRepository.findByEntityType(entityType));
    }

    /** Undecided requests of one entity type, regardless of which record they target. */
    public List<ApprovalRequest> getPendingByEntityType(String entityType) {
        return onlyInScope(requestRepository.findByEntityTypeAndStatusIn(
                entityType,
                List.of(ApprovalStatus.PENDING, ApprovalStatus.IN_PROGRESS, ApprovalStatus.APPROVED)));
    }

    public List<ApprovalRequest> getPendingForEntity(String entityType, String entityId) {
        return onlyInScope(requestRepository.findByEntityTypeAndEntityIdAndStatusIn(
                entityType, entityId,
                List.of(ApprovalStatus.PENDING, ApprovalStatus.IN_PROGRESS, ApprovalStatus.APPROVED)));
    }

    /** Eager-load config so JSON serialization works after the transaction closes. */
    private ApprovalRequest reloadForResponse(Long requestId) {
        return requestRepository.findByIdWithConfig(requestId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Request not found: " + requestId));
    }

    /**
     * Get all requests with pagination (scoped by current user's country access).
     */
    public Page<ApprovalRequest> getAllRequests(Pageable pageable) {
        CountryScopeService.CountryScope scope = countryScopeService.getScope();
        Page<ApprovalRequest> page;
        if (scope.isMinistryUser()) {
            // findAllWithDetails, not the inherited findAll: the latter leaves
            // ApprovalRequest.config an uninitialised proxy and Jackson then throws
            // outside the session. Same reason the scoped branch below fetches too.
            page = requestRepository.findAllWithDetails(pageable);
        } else {
            List<Integer> ids = scope.getAllowedCountryIds();
            if (ids.isEmpty()) {
                return Page.empty(pageable);
            }
            page = requestRepository.findAllWithCountryFilter(pageable, ids);
        }
        populateRequesterNames(page);
        return page;
    }

    /**
     * Completed approval rows: {@link ApprovalStatus#APPROVED} (awaiting execute), {@link ApprovalStatus#EXECUTED},
     * {@link ApprovalStatus#REJECTED}. Scoped by country like {@link #getAllRequests(Pageable)}.
     */
    public Page<ApprovalRequest> getCompletedHistory(Pageable pageable) {
        return getCompletedHistory(pageable, null, null, null);
    }

    public Page<ApprovalRequest> getCompletedHistory(Pageable pageable,
            RequestType requestType, String entityType, ApprovalStatus status) {
        CountryScopeService.CountryScope scope = countryScopeService.getScope();
        Page<ApprovalRequest> page;
        if (scope.isMinistryUser()) {
            page = requestRepository.findCompletedHistory(pageable, requestType, entityType, status);
        } else {
            List<Integer> ids = scope.getAllowedCountryIds();
            if (ids.isEmpty()) return Page.empty(pageable);
            page = requestRepository.findCompletedHistoryWithCountryFilter(pageable, ids, requestType, entityType, status);
        }
        populateRequesterNames(page);
        return page;
    }

    private void populateRequesterNames(Page<ApprovalRequest> page) {
        page.getContent().forEach(r -> {
            if (r.getRequester() != null) {
                String name = r.getRequester().getFirstName() != null
                        ? (r.getRequester().getFirstName() + " " + (r.getRequester().getLastName() != null ? r.getRequester().getLastName() : "")).trim()
                        : r.getRequester().getUserName();
                r.setRequesterFullName(name);
            }
        });
    }

    /**
     * Writes one audit row per rule the super admin was let past, under the
     * action names the earlier passes used (O-01, O-02, N-03.c, N-04), and raises
     * the Wafar write key when the signature will execute a Wafar change (N-04.d).
     */
    private void auditExemptions(Decision decision, Long approverId, ApprovalRequest request,
                                 com.dilaw.mohe.approval.model.ApprovalLevel level) {
        Long requestId = request.getId();
        String at = "level " + request.getCurrentLevel();
        if (decision.via(ApprovalRequestPolicy.SELF_APPROVAL_BYPASS)) {
            auditLogService.log(approverId, "APPROVAL_SELF_APPROVAL_BYPASS", "APPROVAL_REQUEST", requestId,
                    request.getEntityType(), at);
        }
        if (decision.via(ApprovalRequestPolicy.WAFAR_SUPER_ADMIN)) {
            auditLogService.log(approverId, "WAFAR_SUPERADMIN_APPROVE", "APPROVAL_REQUEST", requestId,
                    request.getEntityType(), at);
            wafarWriteKey.elevateCurrentTransaction("super admin signing " + request.getEntityType()
                    + " request " + requestId + " (D-13 b)");
        }
        if (decision.via(ApprovalRequestPolicy.ROLE_BYPASS)) {
            auditLogService.log(approverId, "APPROVAL_ROLE_BYPASS", "APPROVAL_REQUEST", requestId,
                    "requiredRoleId=" + (level == null ? null : level.getRequiredRoleId()), at);
        }
        if (decision.via(ApprovalRequestPolicy.POSITION_BYPASS)) {
            auditLogService.log(approverId, "APPROVAL_ROLE_BYPASS", "APPROVAL_REQUEST", requestId,
                    "position/named approver", at);
        }
        if (decision.via(ApprovalRequestPolicy.SEPARATE_LEVELS_BYPASS)) {
            auditLogService.log(approverId, "APPROVAL_SEPARATE_LEVELS_BYPASS", "APPROVAL_REQUEST", requestId,
                    "approved an earlier level of the same request", at);
        }
    }

    /**
     * Who approved the request's earlier levels (live QA M3). Only APPROVED steps
     * count: a rejection also records its author in {@code approvedBy}.
     */
    /**
     * FIN-06: the level after {@code current} in the request's own chain, or null when
     * {@code current} is its last. Steps are created from the levels active when the
     * request was raised and never change afterwards, so neither editing the config
     * nor deactivating a level alters how many signatures an in-flight request needs.
     */
    static Integer nextLevelAfter(List<ApprovalStep> steps, Integer current) {
        Integer next = null;
        for (ApprovalStep s : steps) {
            Integer order = s.getLevelOrder();
            if (order != null && current != null && order > current && (next == null || order < next)) {
                next = order;
            }
        }
        return next;
    }

    /** FIN-06: a new request waits at its first step, which is level 1 only while level 1 is active. */
    static Integer firstLevelOf(List<ApprovalLevel> levels) {
        return levels.stream().map(ApprovalLevel::getLevelOrder).filter(Objects::nonNull)
                .min(Integer::compare).orElse(1);
    }

    static java.util.Set<Long> priorApprovers(List<ApprovalStep> steps, ApprovalRequest request) {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (ApprovalStep s : steps) {
            if (s.getStatus() == ApprovalStatus.APPROVED && s.getApprovedBy() != null
                    && s.getLevelOrder() != null && request.getCurrentLevel() != null
                    && s.getLevelOrder() < request.getCurrentLevel()) {
                ids.add(s.getApprovedBy());
            }
        }
        return ids;
    }

    /**
     * N-03.d (6 Sep 2026): the requests the caller may sign now — in scope, and
     * eligible under {@link ApprovalRequestPolicy} for the waiting level. Paged in
     * memory: eligibility needs the level, and the open set is small.
     *
     * <p>The waiting steps are read in one query for the whole set (review A1); this
     * used to cost a query per open request, on every page and every badge refresh.
     * The rule itself stays in the policy rather than being restated in SQL.
     */
    @Transactional(readOnly = true)
    public Page<ApprovalRequest> getPendingForMe(Pageable pageable, RequestType requestType, String entityType) {
        List<ApprovalRequest> inScope = getAllPending(Pageable.unpaged(), requestType, entityType).getContent();
        AccessSubject me = accessControl.currentSubject();
        Map<Long, Waiting> waiting = waiting(inScope);
        List<ApprovalRequest> eligible = inScope.stream()
                .filter(r -> accessControl.decideAs(me, waiting.getOrDefault(r.getId(), Waiting.NONE).context(r), "APPROVE").allowed())
                .toList();
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(eligible);
        }
        int from = (int) Math.min(pageable.getOffset(), eligible.size());
        int to = Math.min(from + pageable.getPageSize(), eligible.size());
        return new PageImpl<>(eligible.subList(from, to), pageable, eligible.size());
    }

    /**
     * Marks each request of a pending list with what the caller may do on it now
     * (FIX-PLAN D3-a). The same decisions {@link #approveStep} and
     * {@link #rejectRequest} will take, so a role that approves by chain
     * configuration — Student Officer, Employee — sees the button although it
     * holds no APP_PENDING.APPROVE code (QA #61), and an account that may only
     * look does not.
     *
     * <p>Transactional of its own (round-5 QA R5-01): the controller calls it
     * after the list's read-only transaction has ended, and the policy reads each
     * waiting level's named approvers, a lazy collection. Without a session that
     * read failed for exactly the callers who pass the role and position checks,
     * so the list answered 500 to everyone who had something to approve.
     */
    @Transactional(readOnly = true)
    public Page<ApprovalRequest> withActions(Page<ApprovalRequest> page) {
        withActions(page.getContent());
        return page;
    }

    /** The same for a plain list (a student's open requests on the profile). */
    @Transactional(readOnly = true)
    public List<ApprovalRequest> withActions(List<ApprovalRequest> requests) {
        AccessSubject me = accessControl.currentSubject();
        Map<Long, Waiting> waiting = waiting(requests);
        for (ApprovalRequest r : requests) {
            Waiting w = waiting.getOrDefault(r.getId(), Waiting.NONE);
            ApprovalDecisionContext ctx = w.context(r);
            r.setCanApprove(w.step() != null && accessControl.decideAs(me, ctx, "APPROVE").allowed());
            r.setCanReject(accessControl.decideAs(me, ctx, "REJECT").allowed());
        }
        return requests;
    }

    /** The step a request waits on, and who approved its earlier levels (M3). */
    private record Waiting(ApprovalStep step, java.util.Set<Long> priorApprovers) {
        static final Waiting NONE = new Waiting(null, java.util.Set.of());

        ApprovalDecisionContext context(ApprovalRequest request) {
            return new ApprovalDecisionContext(request, step == null ? null : step.getLevel(), priorApprovers);
        }
    }

    /**
     * For each request: the step it waits on (its current level) and its earlier
     * approvers, by request id, from one read of all their steps. Chunked under SQL
     * Server's 2100-parameter cap.
     */
    private Map<Long, Waiting> waiting(List<ApprovalRequest> requests) {
        Map<Long, ApprovalRequest> byId = new java.util.HashMap<>();
        for (ApprovalRequest r : requests) byId.put(r.getId(), r);
        List<Long> ids = new java.util.ArrayList<>(byId.keySet());
        Map<Long, List<ApprovalStep>> steps = new java.util.HashMap<>();
        for (int i = 0; i < ids.size(); i += 1000) {
            for (ApprovalStep s : stepRepository.findWithLevelByRequestIdIn(ids.subList(i, Math.min(i + 1000, ids.size())))) {
                steps.computeIfAbsent(s.getRequest().getId(), k -> new java.util.ArrayList<>()).add(s);
            }
        }
        Map<Long, Waiting> out = new java.util.HashMap<>();
        steps.forEach((requestId, list) -> {
            ApprovalRequest r = byId.get(requestId);
            ApprovalStep current = list.stream()
                    .filter(s -> java.util.Objects.equals(s.getLevelOrder(), r.getCurrentLevel()))
                    .findFirst().orElse(null);
            out.put(requestId, new Waiting(current, priorApprovers(list, r)));
        });
        return out;
    }

    /** The badge: how many requests wait for the caller in particular. */
    @Transactional(readOnly = true)
    public long countPendingForMe() {
        return getPendingForMe(Pageable.unpaged(), null, null).getTotalElements();
    }

    /**
     * Record an action in the history table.
     */
    private void recordHistory(ApprovalRequest request, String action, Long actionBy,
            Integer levelOrder, String oldStatus, String newStatus, String details) {
        ApprovalHistory history = new ApprovalHistory();
        history.setRequest(request);
        history.setAction(action);
        history.setActionBy(actionBy);
        history.setActionAt(Instant.now());
        history.setLevelOrder(levelOrder);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setDetails(details);
        historyRepository.save(history);
    }

    /**
     * Get history for a request.
     */
    public List<ApprovalHistory> getRequestHistory(Long requestId) {
        // The history is reached by request id, so it needs the same gate the
        // request itself does — otherwise who approved another attaché's change,
        // when, and with what comment is readable by id alone.
        if (getRequestById(requestId).isEmpty()) {
            return List.of();
        }
        return historyRepository.findByRequestIdOrderByActionAtDesc(requestId);
    }

    /**
     * Count pending requests.
     */
    /**
     * Get IDs of entities of a specific type that have pending requests.
     */
    public List<String> getPendingEntityIds(String entityType) {
        // Drives the "has a pending change" badge on list screens. Unscoped, it
        // told a country officer which of another attaché's records were mid-edit.
        return onlyInScope(requestRepository.findByEntityTypeAndStatusIn(entityType,
                List.of(ApprovalStatus.PENDING, ApprovalStatus.IN_PROGRESS)))
                .stream()
                .map(ApprovalRequest::getEntityId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Count pending requests.
     */
    /** The badge count on the navigation bar. Counts only what the caller could open. */
    public long countPending() {
        return getAllPending(PageRequest.of(0, 1)).getTotalElements();
    }

    /**
     * Open requests per entity type in the caller's scope, most numerous first.
     * Empty for a caller with no countries — not the ministry's figures.
     */
    public Map<String, Long> countPendingByEntityType() {
        CountryScopeService.CountryScope scope = countryScopeService.getScope();
        if (!scope.isMinistryUser() && scope.getAllowedCountryIds().isEmpty()) {
            return Map.of();
        }
        boolean unscoped = scope.isMinistryUser();
        List<Integer> countryIds = unscoped ? List.of(-1) : scope.getAllowedCountryIds();
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (Object[] row : requestRepository.countPendingByEntityType(unscoped, countryIds)) {
            counts.put(row[0] == null ? "UNKNOWN" : row[0].toString(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    /** The caller's own open requests, in their current scope (see {@link #getMyRequests}). */
    public long countMyOpenRequests(Long userId) {
        CountryScopeService.CountryScope scope = countryScopeService.getScope();
        if (!scope.isMinistryUser() && scope.getAllowedCountryIds().isEmpty()) {
            return 0L;
        }
        boolean unscoped = scope.isMinistryUser();
        List<Integer> countryIds = unscoped ? List.of(-1) : scope.getAllowedCountryIds();
        return requestRepository.countOpenRequestedBy(userId, unscoped, countryIds);
    }
}
