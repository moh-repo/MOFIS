package com.dilaw.mohe.ums.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "Permissions", schema = "UMSCORE")
// A proxy of this class must still serialise: the two fields are Hibernate's, not the row's.
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class UmsPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "PermissionCode", nullable = false, unique = true, length = 100)
    private String permissionCode; // e.g., 'STUDENTS.VIEW', 'FINANCE.EDIT'

    @Column(name = "PermissionName", nullable = false, length = 200)
    private String permissionName;

    @Column(name = "PermissionNameAr", length = 200)
    private String permissionNameAr;

    @Column(name = "ResourceType", nullable = false, length = 50)
    private String resourceType; // PAGE, SECTION, FIELD, REPORT

    @Column(name = "ResourceId", insertable = false, updatable = false)
    private Long resourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ResourceId")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private UmsResource resource;

    @Column(name = "ActionType", nullable = false, length = 50)
    private String actionType; // SEARCH, VIEW, ADD, EDIT, DELETE, EXPORT

    @Column(name = "Description", length = 500)
    private String description;

    @Column(name = "IsActive", nullable = false)
    private Boolean isActive = true;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
