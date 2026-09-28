package com.dilaw.mohe.controler;

import com.dilaw.mohe.approval.model.ApprovalStatus;

import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.dto.StudentExtensionDto;
import com.dilaw.mohe.dto.StudentExtensionRequest;
import com.dilaw.mohe.service.StudentExtensionMetadataService;
import com.dilaw.mohe.service.StudentExtensionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Scholarship extensions. Reads under {@code STUDENT_EXTENSION} (VIEW and EXPORT
 * included); writes carry {@code STUDENT_EXTENSION_WRITE} (plan item 1.5).
 * Approval of an extension happens in the approval workflow, which has its own
 * gate; the write constant covers the requests opened here.
 */
@RestController
@RequestMapping("/api/student-extension")
@PreAuthorize(MoheSecurityExpressions.STUDENT_EXTENSION)
@RequiredArgsConstructor
public class StudentExtensionController {

    private final StudentExtensionService service;
    private final StudentExtensionMetadataService metadataService;
    private final ApprovalIntegrationService approvalIntegrationService;

    @GetMapping
    public ResponseEntity<List<StudentExtensionDto>> search(
            @RequestParam(required = false) Integer studentId,
            @RequestParam(required = false) Integer filterStudentId,
            @RequestParam(required = false) String extensionType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String passport,
            @RequestHeader(name = "Accept-Language", required = false) String lang) {
        return ResponseEntity.ok(service.search(studentId, filterStudentId, extensionType, status, passport, lang));
    }

    @GetMapping("/{extensionId}")
    public ResponseEntity<StudentExtensionDto> findById(
            @PathVariable Integer extensionId,
            @RequestHeader(name = "Accept-Language", required = false) String lang) {
        return ResponseEntity.ok(service.getById(extensionId, lang));
    }

    @GetMapping("/metadata")
    public ResponseEntity<?> metadata(
            @RequestHeader(name = "Accept-Language", required = false) String lang) {
        return ResponseEntity.ok(metadataService.getMetadata(lang));
    }

    @PostMapping
    @PreAuthorize(MoheSecurityExpressions.STUDENT_EXTENSION_WRITE)
    public ResponseEntity<?> create(@RequestBody StudentExtensionRequest request, HttpServletRequest httpRequest) {
        try {
            Long userId = SecurityRequestUtils.requireUserId();
            approvalIntegrationService.createStudentExtensionRequest(
                    RequestType.INSERT, request, null, userId);
            return ResponseEntity.ok(Map.of("message", "Extension request submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a student the caller may not reach keeps its 403 / 404 (N14)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{extensionId}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_EXTENSION_WRITE)
    public ResponseEntity<?> update(@PathVariable Integer extensionId, @RequestBody StudentExtensionRequest request, HttpServletRequest httpRequest) {
        try {
            Long userId = SecurityRequestUtils.requireUserId();
            // The stored extension's student first (N14: the PUT raised a request for any id).
            service.getById(extensionId);
            approvalIntegrationService.createStudentExtensionRequest(
                    RequestType.UPDATE, request, extensionId, userId);
            return ResponseEntity.ok(Map.of("message", "Update request submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a student the caller may not reach keeps its 403 / 404 (N14)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{extensionId}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_EXTENSION_WRITE)
    public ResponseEntity<?> delete(@PathVariable Integer extensionId, HttpServletRequest httpRequest) {
        try {
            Long userId = SecurityRequestUtils.requireUserId();
            StudentExtensionDto existing = service.getById(extensionId);
            StudentExtensionRequest deleteRequest = new StudentExtensionRequest();
            deleteRequest.setStudentId(existing.getStudentId());
            deleteRequest.setExtensionType(existing.getExtensionType());
            deleteRequest.setExtensionStatus(existing.getExtensionStatus());
            approvalIntegrationService.createStudentExtensionRequest(
                    RequestType.DELETE, deleteRequest, extensionId, userId);
            return ResponseEntity.ok(Map.of("message", "Delete request submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a student the caller may not reach keeps its 403 / 404 (N14)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}