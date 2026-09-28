package com.dilaw.mohe.ums.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * UMSCORE.UMS_Reports — since V073 the report catalogue: a row per hub
 * ({@code parentCode} null) and per card ({@code parentCode} = the hub's
 * code), with the icon, colour, route (plus optional query string) and the
 * page code / permission codes the frontend filters on. Deactivating a row
 * removes the card from its hub.
 */
@Getter
@Setter
@Entity
@Table(name = "UMS_Reports", schema = "UMSCORE")
public class UmsReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "ReportCode", nullable = false, unique = true, length = 100)
    private String reportCode;

    @Column(name = "ReportName", nullable = false, length = 200)
    private String reportName;

    @Column(name = "ReportNameAr", length = 200)
    private String reportNameAr;

    @Column(name = "Description", length = 500)
    private String description;

    @Column(name = "DescriptionAr", length = 500)
    private String descriptionAr;

    @Column(name = "Category", length = 100)
    private String category;

    @Column(name = "Route", length = 255)
    private String route;

    @Column(name = "IsActive", nullable = false)
    private Boolean isActive = true;

    /** The hub this card sits in; null for a hub or a landing-page card. */
    @Column(name = "ParentCode", length = 100)
    private String parentCode;

    /** PrimeIcons name, e.g. {@code pi-users}. */
    @Column(name = "Icon", length = 50)
    private String icon;

    /** Colour token as the hub templates use it, e.g. {@code blue-600} or {@code cyan}. */
    @Column(name = "Color", length = 30)
    private String color;

    /** Query string appended to the route, e.g. {@code doc=id-card}. */
    @Column(name = "QueryParams", length = 200)
    private String queryParams;

    /** The page code that opens the card, matching the route guard. */
    @Column(name = "PageCode", length = 100)
    private String pageCode;

    /** Comma-separated any-of page codes, when the route accepts several. */
    @Column(name = "PermissionCodes", length = 400)
    private String permissionCodes;

    @Column(name = "SortOrder", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (sortOrder == null) sortOrder = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
