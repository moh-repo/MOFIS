package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

/**
 * A file attached to a finance recipient (STUDY_FIN_RECEIVED_WHOM). Stored encrypted at
 * rest under uploads/finance-recipients/documents, mirroring the student-document layout.
 */
@Getter
@Setter
@Entity
@Table(name = "FIN_RECEIVED_WHOM_DOCUMENTS", schema = "MOHECORE")
public class FinReceivedWhomDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DOCUMENT_ID", nullable = false)
    private Integer documentId;

    /** Owning finance recipient (STUDY_FIN_RECEIVED_WHOM.id). */
    @Column(name = "WHOM_ID", nullable = false)
    private Integer whomId;

    @Nationalized
    @Column(name = "DOCUMENT_NAME", nullable = false)
    private String documentName;

    @Nationalized
    @Column(name = "DOCUMENT_TYPE")
    private String documentType;

    @Nationalized
    @Column(name = "FILE_PATH", nullable = false)
    private String filePath;

    @Nationalized
    @Column(name = "ORIGINAL_FILENAME")
    private String originalFilename;

    @Column(name = "FILE_SIZE")
    private Long fileSize;

    @Column(name = "UPLOAD_DATE")
    private Instant uploadDate;

    @Column(name = "IS_ACTIVE")
    private Boolean isActive = true;

    @Column(name = "FILE_HASH", length = 64)
    private String fileHash;

    @Column(name = "IS_ENCRYPTED")
    private Boolean isEncrypted = false;

    public FinReceivedWhomDocument() {
    }

    public FinReceivedWhomDocument(Integer whomId, String documentName, String documentType,
                                   String filePath, String originalFilename, Long fileSize) {
        this.whomId = whomId;
        this.documentName = documentName;
        this.documentType = documentType;
        this.filePath = filePath;
        this.originalFilename = originalFilename;
        this.fileSize = fileSize;
        this.uploadDate = Instant.now();
        this.isActive = true;
        this.isEncrypted = false;
    }
}
