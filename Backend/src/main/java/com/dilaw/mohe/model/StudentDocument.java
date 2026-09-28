package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_DOCUMENTS", schema = "MOHECORE")
public class StudentDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DOCUMENT_ID", nullable = false)
    private Integer documentId;

    @Column(name = "STUDENT_ID", nullable = false)
    private Integer studentId;

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

    @Column(name = "VERSION_NO")
    private Integer versionNo = 1;

    @Column(name = "IS_ENCRYPTED")
    private Boolean isEncrypted = false;

    /** Document expiry date (passport, visa, residence permit). NULL = no expiry. */
    @Column(name = "EXPIRY_DATE")
    private java.time.LocalDate expiryDate;

    // Constructors
    public StudentDocument() {
    }

    public StudentDocument(Integer studentId, String documentName, String documentType, 
                          String filePath, String originalFilename, Long fileSize) {
        this.studentId = studentId;
        this.documentName = documentName;
        this.documentType = documentType;
        this.filePath = filePath;
        this.originalFilename = originalFilename;
        this.fileSize = fileSize;
        this.uploadDate = Instant.now();
        this.isActive = true;
        this.versionNo = 1;
        this.isEncrypted = false;
    }
}
