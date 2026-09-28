package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "EXTENSION_STATUS", schema = "MOHECORE")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EXTENSION_STATUS_ID")
    private Integer extensionStatusId;

    @Column(name = "EXTENSION_STATUS_CODE", nullable = false, unique = true, length = 50)
    private String extensionStatusCode;

    @Column(name = "EXTENSION_STATUS_NAME_EN", nullable = false, length = 200)
    private String extensionStatusNameEn;

    @Column(name = "EXTENSION_STATUS_NAME_AR", length = 200)
    private String extensionStatusNameAr;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "STATUS_COLOR", length = 50)
    private String statusColor;

    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @Column(name = "DISPLAY_ORDER")
    private Integer displayOrder;

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
            createdBy = "system";
        }
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedDate = LocalDateTime.now();
        if (modifiedBy == null) {
            modifiedBy = "system";
        }
    }
}

