package com.dilaw.mohe.model;

import com.dilaw.mohe.util.SecurityContextHelper;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "EXTENSION_TYPE", schema = "MOHECORE")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EXTENSION_TYPE_ID")
    private Integer extensionTypeId;

    @Column(name = "EXTENSION_TYPE_CODE", nullable = false, unique = true, length = 50)
    private String extensionTypeCode;

    @Column(name = "EXTENSION_TYPE_NAME_EN", nullable = false, length = 200)
    private String extensionTypeNameEn;

    @Column(name = "EXTENSION_TYPE_NAME_AR", length = 200)
    private String extensionTypeNameAr;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @Column(name = "DISPLAY_ORDER")
    private Integer displayOrder;

    /** Maximum number of times a student may use this extension type. NULL = unlimited. */
    @Column(name = "MAX_EXTENSIONS")
    private Integer maxExtensions;

    @Column(name = "CREATED_DATE")
    private LocalDateTime createdDate;

    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @Column(name = "MODIFIED_DATE")
    private LocalDateTime modifiedDate;

    @Column(name = "MODIFIED_BY", length = 100)
    private String modifiedBy;

    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        if (createdBy == null) {
            createdBy = SecurityContextHelper.currentUsername();
        }
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedDate = LocalDateTime.now();
        modifiedBy = SecurityContextHelper.currentUsername();
    }
}

