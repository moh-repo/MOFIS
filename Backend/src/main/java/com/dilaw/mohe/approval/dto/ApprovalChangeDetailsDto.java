package com.dilaw.mohe.approval.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * What an approver needs to judge a request: which fields change, and which files
 * are waiting to be written if it is approved.
 */
public class ApprovalChangeDetailsDto {

    /** A single field's before/after. */
    public record FieldChange(String field, String oldValue, String newValue) {}

    /** A file staged with the request, retrievable through the staged-file endpoint. */
    public record PendingFile(String fileName, String originalName, String documentType, boolean image) {}

    /** A stored document this request would remove; still viewable until it is approved. */
    public record RemovedDocument(Integer documentId, String documentName,
            String documentType, String originalFilename) {}

    private Long requestId;
    private String entityType;
    private String entityId;
    private String requestType;
    private String status;

    private List<FieldChange> fieldChanges = new ArrayList<>();

    /** True when a replacement profile photo is staged with this request. */
    private boolean photoChanged;
    /** Staged photo, when {@link #photoChanged}. */
    private PendingFile photo;
    /** Documents that will be added to the student once approved. */
    private List<PendingFile> documents = new ArrayList<>();
    /** Documents that will be removed from the student once approved. */
    private List<RemovedDocument> removedDocuments = new ArrayList<>();

    /** Set when the payload could not be read, so the UI can say so rather than show nothing. */
    private String parseError;

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<FieldChange> getFieldChanges() { return fieldChanges; }
    public void setFieldChanges(List<FieldChange> fieldChanges) { this.fieldChanges = fieldChanges; }

    public boolean isPhotoChanged() { return photoChanged; }
    public void setPhotoChanged(boolean photoChanged) { this.photoChanged = photoChanged; }

    public PendingFile getPhoto() { return photo; }
    public void setPhoto(PendingFile photo) { this.photo = photo; }

    public List<PendingFile> getDocuments() { return documents; }
    public void setDocuments(List<PendingFile> documents) { this.documents = documents; }

    public List<RemovedDocument> getRemovedDocuments() { return removedDocuments; }
    public void setRemovedDocuments(List<RemovedDocument> removedDocuments) { this.removedDocuments = removedDocuments; }

    public String getParseError() { return parseError; }
    public void setParseError(String parseError) { this.parseError = parseError; }
}
