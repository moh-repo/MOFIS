package com.dilaw.mohe.controler;

import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.FinReceivedWhomDocument;
import com.dilaw.mohe.model.StudyFinReceivedWhom;
import com.dilaw.mohe.repository.FinReceivedWhomDocumentRepository;
import com.dilaw.mohe.service.FileEncryptionService;
import com.dilaw.mohe.service.PendingOnboardingUploadService;
import com.dilaw.mohe.service.StudyFinReceivedWhomService;
import com.dilaw.mohe.util.FileUploadValidator;
import com.dilaw.mohe.util.SafePathUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("api/v1/studyFinReceivedWhom")
@PreAuthorize(MoheSecurityExpressions.FINANCE)
public class StudyFinReceivedWhomController {

    private static final Logger log = LoggerFactory.getLogger(StudyFinReceivedWhomController.class);

    @Autowired
    private StudyFinReceivedWhomService service;

    @Autowired
    private FinReceivedWhomDocumentRepository documentRepository;

    @Autowired
    private ApprovalIntegrationService approvalIntegrationService;

    @Autowired
    private FileEncryptionService fileEncryptionService;

    /** Encrypted-at-rest storage root for finance-recipient documents. */
    private final Path documentStorageLocation =
            Paths.get("uploads/finance-recipients/documents").toAbsolutePath().normalize();

    @GetMapping("/for-delegated/{studentId}")
    public List<StudyFinReceivedWhom> listForDelegatedStudent(@PathVariable Integer studentId) {
        return service.findForDelegatedStudent(studentId);
    }

    @GetMapping("/")
    public List<StudyFinReceivedWhom> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public StudyFinReceivedWhom getById(@PathVariable Integer id) {
        return service.getById(id);
    }

    @PostMapping("/")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> create(@RequestBody StudyFinReceivedWhom entity) {
        try {
            service.validateForRequest(entity); // R5-25: an empty request no longer enters the chain
            approvalIntegrationService.createStudyFinReceivedWhomRequest(
                    RequestType.INSERT, entity, null, null, null, PendingApprovalResponse.currentUserId());
            return ResponseEntity.ok(PendingApprovalResponse.CREATE);
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a student the caller may not reach keeps its 403 / 404 (N14)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * The body is taken as a tree, not a bound entity, so the service can tell a
     * column the dialog left out from one it cleared. See
     * {@code StudyFinReceivedWhomService#update}. The tree is what the request
     * stores, so the same distinction survives until an approver signs off.
     */
    @PutMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody JsonNode body) {
        try {
            StudyFinReceivedWhom original = service.getById(id);
            approvalIntegrationService.createStudyFinReceivedWhomRequest(
                    RequestType.UPDATE, null, body, id, original, PendingApprovalResponse.currentUserId());
            return ResponseEntity.ok(PendingApprovalResponse.UPDATE);
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a student the caller may not reach keeps its 403 / 404 (N14)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Opens a removal request. The row and its documents stay until an approver
     * signs off; clearing the encrypted folder moved into
     * {@code StudyFinReceivedWhomService#delete} with the delete it accompanies,
     * so the executor's path removes the files too.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        try {
            StudyFinReceivedWhom original = service.getById(id);
            approvalIntegrationService.createStudyFinReceivedWhomRequest(
                    RequestType.DELETE, original, null, id, original, PendingApprovalResponse.currentUserId());
            return ResponseEntity.ok(PendingApprovalResponse.DELETE);
        } catch (com.dilaw.mohe.approval.service.PendingApprovalExistsException e) {
            throw e; // 409 PENDING_EXISTS naming the request, in the reader's language (W4-08)
        } catch (Exception e) {
            ScopeRefusal.rethrow(e); // a student the caller may not reach keeps its 403 / 404 (N14)
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


    // ── Documents ─────────────────────────────────────────────────────────────

    /**
     * Active documents attached to a finance recipient, newest first.
     *
     * <p>Through the service, so the caller's country scope decides: the record
     * itself has been scoped since the audit, but its documents were readable by
     * id by anyone holding FINANCE — and they are passport scans.
     */
    @GetMapping("/{id}/documents")
    public List<FinReceivedWhomDocument> getDocuments(@PathVariable Integer id) {
        return service.documentsOf(id);
    }

    /** Uploads a new document for a finance recipient (encrypted at rest). */
    @PostMapping("/{id}/documents")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> uploadDocument(
            @PathVariable Integer id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentName", required = false) String documentName,
            @RequestParam(value = "documentType", required = false) String documentType) {
        try {
            FileUploadValidator.validate(file);
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("No file provided");
            }
            // Confirm the recipient exists (throws if not, keeping orphan documents out).
            service.getById(id);

            FinReceivedWhomDocument doc = storeDocument(id, file, documentName, documentType);
            return ResponseEntity.ok(documentRepository.save(doc));
        } catch (Exception e) {
            log.error("Error uploading finance-recipient document: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Error uploading document: " + e.getMessage());
        }
    }

    /** Encrypts the upload into the recipient's folder and builds the row (unsaved). */
    private FinReceivedWhomDocument storeDocument(Integer whomId, MultipartFile file,
            String documentName, String documentType) throws IOException {
        String origName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String folderName = "whom_" + whomId;
        String timestampedName = PendingOnboardingUploadService.buildTimestampedFilename(origName);

        Path destDir = documentStorageLocation.resolve(folderName).resolve(datePart);
        Files.createDirectories(destDir);
        Path destFile = destDir.resolve(timestampedName);
        fileEncryptionService.encryptMultipartFile(file, destFile);
        String relPath = folderName + "/" + datePart + "/" + timestampedName;

        byte[] plaintext = fileEncryptionService.readDecrypted(destFile);
        FinReceivedWhomDocument doc = new FinReceivedWhomDocument(
                whomId,
                documentName != null && !documentName.isBlank() ? documentName.trim() : origName,
                documentType != null && !documentType.isBlank() ? documentType.trim() : null,
                relPath,
                origName,
                (long) plaintext.length);
        doc.setFileHash(fileEncryptionService.sha256(plaintext));
        doc.setIsEncrypted(true);
        return doc;
    }

    @GetMapping("/documents/{docId}/view")
    public ResponseEntity<?> viewDocument(@PathVariable Integer docId) {
        return serveDocument(docId, false);
    }

    @GetMapping("/documents/{docId}/download")
    @com.dilaw.mohe.audit.AuditedRead(resource = "RECIPIENT_DOCUMENT", format = "DOWNLOAD")
    public ResponseEntity<?> downloadDocument(@PathVariable Integer docId) {
        return serveDocument(docId, true);
    }

    /**
     * The file behind a document id, for the viewer and the download.
     *
     * <p>The scope check is the first thing here and sits outside the try, for
     * two reasons (plan W6-09). It was missing altogether: both endpoints looked
     * the row up by document id alone, so any holder of FINANCE who knew or
     * guessed an id could read another country's passport scans — the list
     * endpoint was scoped and these two were not. And the catch below turns
     * every exception into a 400, so a refusal raised inside it would reach the
     * caller as a bad request instead of the 403 it is.
     */
    private ResponseEntity<?> serveDocument(Integer docId, boolean asAttachment) {
        Optional<FinReceivedWhomDocument> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        FinReceivedWhomDocument doc = docOpt.get();
        service.getById(doc.getWhomId()); // existence and country scope, or it raises

        try {
            Path filePath = SafePathUtils.resolveUnderRoot(documentStorageLocation, doc.getFilePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            byte[] content = fileEncryptionService.readDecrypted(filePath);
            HttpHeaders headers = new HttpHeaders();
            if (asAttachment) {
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                headers.setContentDispositionFormData("attachment", doc.getOriginalFilename());
            } else {
                headers.setContentType(resolveMediaType(doc.getOriginalFilename()));
            }
            headers.setContentLength(content.length);
            return ResponseEntity.ok().headers(headers).body(content);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error serving document: " + e.getMessage());
        }
    }

    /**
     * Soft-deletes the document (keeps the encrypted file for audit).
     *
     * <p>Scoped through the recipient for the same reason the viewer is: this
     * took a document id and nothing else, so one country could retire
     * another's paperwork (W6-09).
     */
    @DeleteMapping("/documents/{docId}")
    @PreAuthorize(MoheSecurityExpressions.FINANCE_WRITE)
    public ResponseEntity<?> deleteDocument(@PathVariable Integer docId) {
        Optional<FinReceivedWhomDocument> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        FinReceivedWhomDocument doc = docOpt.get();
        service.getById(doc.getWhomId()); // existence and country scope, or it raises
        doc.setIsActive(false);
        documentRepository.save(doc);
        return ResponseEntity.ok(Map.of("message", "Document deleted"));
    }

    private MediaType resolveMediaType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
