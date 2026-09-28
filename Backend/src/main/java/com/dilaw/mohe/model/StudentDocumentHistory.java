package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_DOCUMENT_HISTORY", schema = "MOHECORE")
public class StudentDocumentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HISTORY_ID", nullable = false)
    private Integer historyId;

    @Column(name = "DOCUMENT_ID", nullable = false)
    private Integer documentId;

    @Column(name = "STUDENT_ID", nullable = false)
    private Integer studentId;

    @Nationalized
    @Column(name = "DOCUMENT_NAME")
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

    @Column(name = "FILE_HASH", length = 64)
    private String fileHash;

    @Column(name = "VERSION_NO", nullable = false)
    private Integer versionNo;

    @Nationalized
    @Column(name = "ACTION", nullable = false, length = 20)
    private String action;

    @Column(name = "CHANGED_BY")
    private Long changedBy;

    @Column(name = "CHANGED_AT", nullable = false)
    private Instant changedAt;
}
