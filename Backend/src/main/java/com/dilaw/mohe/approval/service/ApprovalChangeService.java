package com.dilaw.mohe.approval.service;

import com.dilaw.mohe.approval.dto.ApprovalChangeDetailsDto;
import com.dilaw.mohe.approval.dto.ApprovalChangeDetailsDto.FieldChange;
import com.dilaw.mohe.approval.dto.ApprovalChangeDetailsDto.PendingFile;
import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.service.FileEncryptionService;
import com.dilaw.mohe.util.JsonValueComparator;
import com.dilaw.mohe.util.SafePathUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Turns a stored approval request into a reviewable summary: the field-level diff between
 * the original and proposed records, plus any files staged alongside it.
 */
@Service
public class ApprovalChangeService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalChangeService.class);

    /**
     * Fields excluded from the diff: identity, server-derived values, and relation objects
     * whose scalar id column is compared instead. Showing these as "changes" would bury the
     * edits an approver actually needs to look at.
     */
    private static final Set<String> IGNORED_FIELDS = Set.of(
            "studentId", "id", "rowVersion", "studentCountry", "studentAge", "picturePath");

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");

    private final Path pendingRoot = Paths.get("uploads/pending-onboarding").toAbsolutePath().normalize();
    private final ObjectMapper objectMapper;
    private final FileEncryptionService fileEncryptionService;

    public ApprovalChangeService(FileEncryptionService fileEncryptionService) {
        this.fileEncryptionService = fileEncryptionService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public ApprovalChangeDetailsDto describe(ApprovalRequest request) {
        ApprovalChangeDetailsDto dto = new ApprovalChangeDetailsDto();
        dto.setRequestId(request.getId());
        dto.setEntityType(request.getEntityType());
        dto.setEntityId(request.getEntityId());
        dto.setRequestType(request.getRequestType() != null ? request.getRequestType().name() : null);
        dto.setStatus(request.getStatus() != null ? request.getStatus().name() : null);

        try {
            JsonNode proposedRoot = readTree(request.getRequestData());
            JsonNode originalRoot = readTree(request.getOriginalData());

            /*
             * A document removal carries the document itself, not a before/after record —
             * the meaningful change is which file disappears, so a field diff of identical
             * snapshots would show nothing at all.
             */
            if ("STUDENT_DOCUMENT".equals(request.getEntityType())) {
                describeDocumentRemoval(dto, proposedRoot);
                return dto;
            }

            // Uploads travel in an envelope; a bare entity means none were attached.
            String sessionId = null;
            JsonNode proposed = proposedRoot;
            if (proposedRoot != null && proposedRoot.hasNonNull("studentData")) {
                sessionId = proposedRoot.path("pendingUploadSessionId").asText(null);
                proposed = proposedRoot.get("studentData");
            }

            /*
             * A deletion stores the same record on both sides, so a straight diff finds
             * nothing. Present the record's values as being removed instead — an approver
             * signing off a deletion needs to see what is going away.
             */
            if (request.getRequestType() == com.dilaw.mohe.approval.model.RequestType.DELETE) {
                dto.setFieldChanges(asRemovals(proposed != null ? proposed : originalRoot));
                return dto;
            }

            dto.setFieldChanges(diff(originalRoot, proposed));
            applyStagedFiles(dto, sessionId);
        } catch (Exception e) {
            log.warn("Could not describe approval request {}: {}", request.getId(), e.getMessage());
            dto.setParseError(e.getMessage());
        }
        return dto;
    }

    /** Every populated scalar of a record, presented as a value that is going away. */
    private List<FieldChange> asRemovals(JsonNode record) {
        List<FieldChange> removals = new ArrayList<>();
        if (record == null || !record.isObject()) {
            return removals;
        }
        Iterator<String> fields = record.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (IGNORED_FIELDS.contains(field)) {
                continue;
            }
            JsonNode value = record.get(field);
            if (value == null || value.isObject() || value.isArray()
                    || JsonValueComparator.isEmptyValue(value)) {
                continue;
            }
            removals.add(new FieldChange(field, JsonValueComparator.asDisplayText(value), ""));
        }
        return removals;
    }

    private void describeDocumentRemoval(ApprovalChangeDetailsDto dto, JsonNode document) {
        if (document == null || !document.isObject()) {
            return;
        }
        dto.setRemovedDocuments(List.of(new ApprovalChangeDetailsDto.RemovedDocument(
                document.path("documentId").isMissingNode() ? null : document.path("documentId").asInt(),
                document.path("documentName").asText(null),
                document.path("documentType").asText(null),
                document.path("originalFilename").asText(null))));
    }

    /**
     * Which student a request concerns, so per-student views can gather requests whose
     * entity id is not the student itself (a document removal is keyed on the document).
     */
    public Integer studentIdOf(ApprovalRequest request) {
        try {
            JsonNode root = readTree(request.getRequestData());
            if (root == null) {
                return null;
            }
            JsonNode entity = root.hasNonNull("studentData") ? root.get("studentData") : root;
            JsonNode studentId = entity.get("studentId");
            return studentId != null && studentId.isNumber() ? studentId.asInt() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode readTree(String json) throws IOException {
        return json == null || json.isBlank() ? null : objectMapper.readTree(json);
    }

    /**
     * Compares every scalar field present on either side.
     *
     * On INSERT there is no original, so each populated field is reported as an addition —
     * which is what an approver reviewing a new student needs to see.
     */
    private List<FieldChange> diff(JsonNode original, JsonNode proposed) {
        List<FieldChange> changes = new ArrayList<>();
        if (proposed == null || !proposed.isObject()) {
            return changes;
        }

        Set<String> fields = new LinkedHashSet<>();
        proposed.fieldNames().forEachRemaining(fields::add);
        if (original != null && original.isObject()) {
            Iterator<String> originalFields = original.fieldNames();
            while (originalFields.hasNext()) {
                fields.add(originalFields.next());
            }
        }

        for (String field : fields) {
            if (IGNORED_FIELDS.contains(field)) {
                continue;
            }
            JsonNode proposedValue = proposed.get(field);
            JsonNode originalValue = original != null ? original.get(field) : null;

            // Relations and collections are represented by their id columns.
            if ((proposedValue != null && (proposedValue.isObject() || proposedValue.isArray()))
                    || (originalValue != null && (originalValue.isObject() || originalValue.isArray()))) {
                continue;
            }
            if (JsonValueComparator.valuesEquivalent(proposedValue, originalValue)) {
                continue;
            }
            changes.add(new FieldChange(
                    field,
                    JsonValueComparator.asDisplayText(originalValue),
                    JsonValueComparator.asDisplayText(proposedValue)));
        }
        return changes;
    }

    /** Lists what is waiting in the staging folder for this request. */
    private void applyStagedFiles(ApprovalChangeDetailsDto dto, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        Path dir = pendingRoot.resolve(sessionId.strip()).normalize();
        if (!dir.startsWith(pendingRoot) || !Files.isDirectory(dir)) {
            return;
        }

        List<PendingFile> documents = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".meta.txt")) {
                    continue;
                }
                String[] meta = readMeta(file);
                PendingFile pending = new PendingFile(name, meta[0], meta[1], isImage(meta[0]));

                if (name.startsWith("profile_")) {
                    dto.setPhotoChanged(true);
                    dto.setPhoto(pending);
                } else {
                    documents.add(pending);
                }
            }
        } catch (IOException e) {
            log.warn("Could not list staged uploads for session {}: {}", sessionId, e.getMessage());
        }
        dto.setDocuments(documents);
    }

    /** @return [originalName, documentType]; type is null when the uploader chose none. */
    private String[] readMeta(Path file) {
        Path meta = Paths.get(file.toString() + ".meta.txt");
        String fallbackName = file.getFileName().toString();
        if (!Files.exists(meta)) {
            return new String[] { fallbackName, null };
        }
        try {
            String[] lines = Files.readString(meta).split("\\R", 2);
            String name = lines[0].isBlank() ? fallbackName : lines[0].trim();
            String type = lines.length > 1 && !lines[1].isBlank() ? lines[1].trim() : null;
            return new String[] { name, type };
        } catch (IOException e) {
            return new String[] { fallbackName, null };
        }
    }

    private boolean isImage(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return false;
        }
        return IMAGE_EXTENSIONS.contains(
                fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT));
    }

    /**
     * Decrypted bytes of one staged file, for previewing before approval.
     *
     * @return null when the request carries no such staged file.
     */
    public byte[] readStagedFile(ApprovalRequest request, String fileName) {
        try {
            JsonNode root = readTree(request.getRequestData());
            if (root == null || !root.hasNonNull("pendingUploadSessionId")) {
                return null;
            }
            String sessionId = root.get("pendingUploadSessionId").asText();
            Path dir = pendingRoot.resolve(sessionId.strip()).normalize();
            // Resolved under the session folder so a crafted name cannot escape it.
            Path file = SafePathUtils.resolveUnderRoot(dir, fileName);
            if (!Files.exists(file)) {
                return null;
            }
            return fileEncryptionService.readDecrypted(file);
        } catch (Exception e) {
            log.warn("Could not read staged file {} for request {}: {}",
                    fileName, request.getId(), e.getMessage());
            return null;
        }
    }
}
