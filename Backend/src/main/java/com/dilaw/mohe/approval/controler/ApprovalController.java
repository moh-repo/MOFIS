package com.dilaw.mohe.approval.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.ApprovalHistory;
import com.dilaw.mohe.approval.model.ApprovalStatus;
import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalWorkflowService;
import com.dilaw.mohe.approval.service.ApprovalChangeService;
import com.dilaw.mohe.approval.dto.ApprovalChangeDetailsDto;
import com.dilaw.mohe.approval.dto.ApprovalSummaryDto;
import com.dilaw.mohe.util.SortWhitelist;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for approval request management.
 */
@RestController
@RequestMapping("api/v1/approvals")
@PreAuthorize(MoheSecurityExpressions.APPROVALS)
public class ApprovalController {

    /** Properties of {@link ApprovalRequest} the lists may be ordered by (plan item 2.4). */
    static final java.util.Set<String> SORTABLE = java.util.Set.of(
            "id", "requestType", "entityType", "entityId", "countryId", "currentLevel", "status",
            "requestedBy", "requestedAt", "rejectedBy", "rejectedAt", "executedBy", "executedAt",
            "parentApprovalRequestId");

    @Autowired
    private ApprovalWorkflowService workflowService;

    @Autowired
    private ApprovalChangeService changeService;

    /**
     * Get all approval requests with pagination.
     */
    @GetMapping
    public ResponseEntity<Page<ApprovalRequest>> getAllRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "requestedAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {

        Sort sort = SortWhitelist.sort(sortField, sortOrder, SORTABLE);
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(workflowService.getAllRequests(pageable));
    }

    /**
     * Pending approval requests.
     *
     * <p>{@code scope=mine} (the default since N-03.d, 6 Sep 2026) lists the
     * requests the caller is eligible to sign at their current level — role,
     * position, named approvers, Wafar ownership and country all applied.
     * {@code scope=all} lists every open request in the caller's country scope,
     * for oversight.
     */
    @GetMapping("/pending")
    public ResponseEntity<Page<ApprovalRequest>> getPendingRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String requestType,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "mine") String scope) {
        Pageable pageable = PageRequest.of(page, size);
        RequestType rt = requestType != null && !requestType.isBlank() ? RequestType.valueOf(requestType) : null;
        if ("all".equalsIgnoreCase(scope)) {
            return ResponseEntity.ok(workflowService.withActions(workflowService.getAllPending(pageable, rt, entityType)));
        }
        return ResponseEntity.ok(workflowService.withActions(workflowService.getPendingForMe(pageable, rt, entityType)));
    }

    /**
     * Paginated list of finished workflows: approved (awaiting execution), executed, or rejected.
     */
    @GetMapping("/completed")
    public ResponseEntity<Page<ApprovalRequest>> getCompletedHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "requestedAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(required = false) String requestType,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String status) {
        Sort sort = SortWhitelist.sort(sortField, sortOrder, SORTABLE);
        Pageable pageable = PageRequest.of(page, size, sort);
        RequestType rt = requestType != null && !requestType.isBlank() ? RequestType.valueOf(requestType) : null;
        ApprovalStatus st = status != null && !status.isBlank() ? ApprovalStatus.valueOf(status) : null;
        return ResponseEntity.ok(workflowService.getCompletedHistory(pageable, rt, entityType, st));
    }

    /**
     * Get pending requests for specific roles.
     */
    @GetMapping("/pending/roles")
    public ResponseEntity<List<ApprovalRequest>> getPendingForRoles(
            @RequestParam List<Long> roleIds) {
        return ResponseEntity.ok(workflowService.getPendingForRoles(roleIds));
    }

    /**
     * The caller's own submitted requests.
     *
     * <p><b>The user is taken from the token, never from the request.</b> This used
     * to read a {@code userId} query parameter and pass it straight through, so any
     * authenticated user could page through anyone else's requests by changing a
     * number in the URL — and a request carries {@code RequestData}, the full
     * proposed state of a student record. Country scope would not have helped:
     * this is about identity, not country.
     *
     * <p>The parameter is still accepted so existing clients do not break, but it
     * is only honoured when it matches the token. Anything else is refused rather
     * than silently answered with the caller's own rows, which would look like the
     * feature working.
     *
     * <p>Any signed-in account (round-5 QA R5-16): the list is the caller's own requests,
     * and a Finance Officer who raises requests holds no approvals permission.
     */
    @GetMapping("/my-requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ApprovalRequest>> getMyRequests(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String requestType,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String status) {
        Long currentUserId = SecurityRequestUtils.currentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
        if (userId != null && !userId.equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "my-requests returns your own requests only");
        }
        Pageable pageable = PageRequest.of(page, size);
        RequestType rt = requestType != null && !requestType.isBlank() ? RequestType.valueOf(requestType) : null;
        ApprovalStatus st = status != null && !status.isBlank() ? ApprovalStatus.valueOf(status) : null;
        return ResponseEntity.ok(workflowService.getMyRequests(currentUserId, pageable, rt, entityType, st));
    }

    /**
     * Get a specific request by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApprovalRequest> getRequestById(@PathVariable Long id) {
        return workflowService.getRequestById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * What this request would change: field-level diff plus any files staged with it.
     *
     * <p>Also the requester's own request without the approvals permission (R5-16): the
     * My requests details dialog reads it. The service decides who may read which.
     */
    @GetMapping("/{id}/changes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApprovalChangeDetailsDto> getRequestChanges(@PathVariable Long id) {
        return workflowService.getRequestForViewer(id)
                .map(request -> ResponseEntity.ok(changeService.describe(request)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Streams a file staged with the request so an approver can view a replacement photo
     * or an attached document before deciding. The requester may open their own (R5-16).
     */
    @GetMapping("/{id}/staged/{fileName}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getStagedFile(@PathVariable Long id, @PathVariable String fileName) {
        return workflowService.getRequestForViewer(id)
                .map(request -> {
                    byte[] content = changeService.readStagedFile(request, fileName);
                    if (content == null) {
                        return ResponseEntity.notFound().build();
                    }
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(mediaTypeFor(fileName));
                    headers.setContentLength(content.length);
                    return ResponseEntity.ok().headers(headers).body(content);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Requests still awaiting a decision for one entity, used to flag a record as pending
     * wherever it is displayed.
     */
    @GetMapping("/entity/{entityType}/{entityId}/pending")
    public ResponseEntity<List<ApprovalSummaryDto>> getPendingForEntity(
            @PathVariable String entityType, @PathVariable String entityId) {
        return ResponseEntity.ok(workflowService.getPendingForEntity(entityType, entityId)
                .stream().map(ApprovalSummaryDto::from).toList());
    }

    /**
     * Every request raised for one student, split into those still awaiting a decision and
     * those already decided.
     *
     * Both lists come from one call because the profile shows them together, and a document
     * removal is keyed on the document id — invisible to a query matching only the student
     * id, so those are resolved through the request payload.
     */
    @GetMapping("/student/{studentId}/requests")
    public ResponseEntity<Map<String, List<ApprovalSummaryDto>>> getStudentRequests(
            @PathVariable Integer studentId) {

        List<ApprovalRequest> all =
                new java.util.ArrayList<>(workflowService.getAllForEntity("STUDENT_DATA", String.valueOf(studentId)));

        workflowService.getAllByEntityType("STUDENT_DOCUMENT").stream()
                .filter(request -> studentId.equals(changeService.studentIdOf(request)))
                .forEach(all::add);

        all.sort(java.util.Comparator.comparing(
                ApprovalRequest::getRequestedAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));

        List<ApprovalRequest> open = new java.util.ArrayList<>();
        List<ApprovalSummaryDto> completed = new java.util.ArrayList<>();
        for (ApprovalRequest request : all) {
            ApprovalStatus status = request.getStatus();
            // APPROVED counts as decided: the outcome is settled even if execution trails it.
            boolean decided = status == ApprovalStatus.APPROVED
                    || status == ApprovalStatus.REJECTED
                    || status == ApprovalStatus.EXECUTED;
            if (decided) completed.add(ApprovalSummaryDto.from(request));
            else open.add(request);
        }
        // The profile's Approve/Reject follow the server's decision (FIX-PLAN D3-a).
        List<ApprovalSummaryDto> pending = workflowService.withActions(open).stream()
                .map(ApprovalSummaryDto::from).toList();

        return ResponseEntity.ok(Map.of("pending", pending, "completed", completed));
    }

    private MediaType mediaTypeFor(String fileName) {
        String extension = fileName != null && fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase()
                : "";
        return switch (extension) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    /**
     * Get history for a request.
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<ApprovalHistory>> getRequestHistory(@PathVariable Long id) {
        return ResponseEntity.ok(workflowService.getRequestHistory(id));
    }

    /*
     * Request bodies. These were `@Valid @RequestBody Map<String, Object>`, and
     * `@Valid` on a raw map validates nothing: a body without `requestType` reached
     * `.toUpperCase()` on a null and came back as a 400 whose message was an NPE's.
     * The records carry the constraints for the keys the handlers read; JSON names
     * are unchanged.
     */

    /** {@code POST /approvals}. */
    public record CreateRequestBody(
            @NotBlank String entityType,
            @NotBlank String requestType,
            String entityId,
            String requestData,
            String originalData) {}

    /** {@code POST /approvals/{id}/approve}. */
    public record ApproveBody(@NotNull Long approverId, String comments) {}

    /** {@code POST /approvals/{id}/reject}. */
    public record RejectBody(@NotNull Long rejectedBy, @NotBlank String reason) {}

    /*
     * No catch-all in the three writes below. They used to answer every exception as a
     * 400 carrying e.getMessage(), so a constraint failure during the final approval
     * put the driver's SQL text on the approver's screen (live QA BRD-002). Errors now
     * reach GlobalExceptionHandler: integrity violations become a translated 409 with
     * the detail in the log only, state conflicts a 409, policy refusals a 403, and
     * anything unexpected a logged 500 with a generic message.
     */

    /**
     * Create a new approval request.
     */
    @PostMapping
    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    public ResponseEntity<?> createRequest(@Valid @RequestBody CreateRequestBody payload) {
        long requestedBy = SecurityRequestUtils.currentUserIdOrDefault(1L);
        RequestType requestType;
        try {
            requestType = RequestType.valueOf(payload.requestType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown request type: " + payload.requestType());
        }

        ApprovalRequest request = workflowService.createRequest(
                payload.entityType(), requestType, payload.entityId(),
                payload.requestData(), payload.originalData(), requestedBy);

        return ResponseEntity.ok(request);
    }

    /**
     * Approve the current step of a request.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveRequest(
            @PathVariable Long id,
            @Valid @RequestBody ApproveBody payload) {
        return ResponseEntity.ok(workflowService.approveStep(id, payload.approverId(), payload.comments()));
    }

    /**
     * Reject a request.
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectRequest(
            @PathVariable Long id,
            @Valid @RequestBody RejectBody payload) {
        return ResponseEntity.ok(workflowService.rejectRequest(id, payload.rejectedBy(), payload.reason().trim()));
    }

    /** {@code POST /approvals/{id}/withdraw}. */
    public record WithdrawBody(String reason) {}

    /**
     * The requester takes back their own request (round-5 QA R5-16). Open to any signed-in
     * account, not only to holders of the approvals permission: whoever may raise a
     * request may withdraw it. The service refuses anyone but the requester.
     */
    @PostMapping("/{id}/withdraw")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> withdrawRequest(
            @PathVariable Long id,
            @RequestBody(required = false) WithdrawBody payload) {
        return ResponseEntity.ok(workflowService.withdrawRequest(id, payload == null ? null : payload.reason()));
    }

    /**
     * Get count of pending requests.
     */
    @GetMapping("/count/pending")
    public ResponseEntity<Map<String, Long>> countPending(@RequestParam(defaultValue = "mine") String scope) {
        long count = "all".equalsIgnoreCase(scope) ? workflowService.countPending() : workflowService.countPendingForMe();
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Get IDs of entities of a specific type that have pending requests.
     */
    @GetMapping("/pending-entities")
    public ResponseEntity<List<String>> getPendingEntityIds(
            @RequestParam String entityType,
            @RequestParam(required = false) RequestType requestType) {
        return ResponseEntity.ok(workflowService.getPendingEntityIds(entityType));
    }
}
