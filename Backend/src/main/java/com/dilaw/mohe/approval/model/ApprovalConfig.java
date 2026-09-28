package com.dilaw.mohe.approval.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "ApprovalConfigs", schema = "ALERTCORE")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ApprovalConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "EntityType", nullable = false, unique = true, length = 100)
    private String entityType;

    @Column(name = "EntityTypeAr", length = 100)
    private String entityTypeAr;

    @Column(name = "Description", length = 500)
    private String description;

    @Column(name = "DescriptionAr", length = 500)
    private String descriptionAr;

    @Column(name = "TotalLevels", nullable = false)
    private Integer totalLevels = 2;

    @Column(name = "IsActive", nullable = false)
    private Boolean isActive = true;

    @Column(name = "RequireAllLevels", nullable = false)
    private Boolean requireAllLevels = true;

    @Column(name = "CreatedAt", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "CreatedBy")
    private Long createdBy;

    @Column(name = "UpdatedAt")
    private Instant updatedAt;

    @Column(name = "UpdatedBy")
    private Long updatedBy;

    /** Loaded via {@code /approval-configs/{id}/levels}; omitted from JSON to avoid lazy-init during request serialization. */
    @JsonIgnore
    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("levelOrder ASC")
    private List<ApprovalLevel> levels = new ArrayList<>();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
