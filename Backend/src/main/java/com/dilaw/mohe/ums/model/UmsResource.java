package com.dilaw.mohe.ums.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "Resources", schema = "UMSCORE")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "ResourceType", discriminatorType = DiscriminatorType.STRING)
public class UmsResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "ResourceCode", nullable = false, unique = true, length = 100)
    private String resourceCode;

    @Column(name = "ResourceName", nullable = false, length = 200)
    private String resourceName;

    @Column(name = "ResourceNameAr", length = 200)
    private String resourceNameAr;

    @Column(name = "ResourceType", nullable = false, length = 50, insertable = false, updatable = false)
    private String resourceType; // PAGE, API, REPORT

    @Column(name = "Route", length = 255)
    private String route;

    @Column(name = "Icon", length = 100)
    private String icon;

    @Column(name = "DisplayOrder")
    private Integer displayOrder = 0;

    @Column(name = "ParentResourceId", insertable = false, updatable = false)
    private Long parentResourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ParentResourceId")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private UmsResource parentResource;

    @Column(name = "Description", length = 500)
    private String description;

    @Column(name = "IsActive", nullable = false)
    private Boolean isActive = true;

    @Column(name = "IsVisible", nullable = false)
    private Boolean isVisible = true;

    @Column(name = "FieldType", length = 50)
    private String fieldType;

    @Column(name = "IsSensitive")
    private Boolean isSensitive = false;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
