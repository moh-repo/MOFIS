package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "DOCUMENT_TYPES", schema = "MOHECORE")
public class DocumentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DOCUMENT_TYPE_ID", nullable = false)
    private Integer documentTypeId;

    @Nationalized
    @Column(name = "DOCUMENT_TYPE_NAME", nullable = false, unique = true)
    private String documentTypeName;

    @Column(name = "DOCUMENT_TYPE_NAME_EN")
    private String documentTypeNameEn;

    @Nationalized
    @Column(name = "DOCUMENT_TYPE_DESCRIPTION")
    private String documentTypeDescription;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive = true;

    @Column(name = "SORT_ORDER")
    private Integer sortOrder = 0;

    // Constructors
    public DocumentType() {
    }

    public DocumentType(String documentTypeName, String documentTypeDescription) {
        this.documentTypeName = documentTypeName;
        this.documentTypeDescription = documentTypeDescription;
        this.isActive = true;
        this.sortOrder = 0;
    }

    public DocumentType(String documentTypeName, String documentTypeDescription, Integer sortOrder) {
        this.documentTypeName = documentTypeName;
        this.documentTypeDescription = documentTypeDescription;
        this.isActive = true;
        this.sortOrder = sortOrder;
    }
}
