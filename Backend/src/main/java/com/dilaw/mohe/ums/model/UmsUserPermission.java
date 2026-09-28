package com.dilaw.mohe.ums.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * A permission granted to one account directly, outside any role.
 *
 * <p>Security plan R-14 (6 Sep 2026): a direct grant is a <em>time-boxed
 * exception</em>. It carries the reason it was made and the date it lapses
 * ({@code ums.direct-grant.max-days}, 90 by default); an expired row is ignored
 * by every permission read and deactivated by the nightly review. The one kind
 * of direct grant that is configuration rather than exception (the per-user
 * dashboard sections of V071, {@code DASHBOARD_*.VIEW}) needs neither.
 */
@Getter
@Setter
@Entity
@Table(name = "UserPermissions", schema = "UMSCORE", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "UserId", "PermissionId" })
})
public class UmsUserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserId", nullable = false)
    private UmsUser user;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PermissionId", nullable = false)
    private UmsPermission permission;

    @Column(name = "GrantedAt", nullable = false)
    private LocalDateTime grantedAt;

    @Column(name = "GrantedBy")
    private Long grantedBy;

    @Column(name = "IsActive", nullable = false)
    private Boolean isActive = true;

    /** Why the exception was made (R-14, V091). Required unless the code is per-user configuration. */
    @Column(name = "Reason", length = 500)
    private String reason;

    /** When the exception lapses (R-14, V091); null only for per-user configuration codes. */
    @Column(name = "ExpiresAt")
    private LocalDateTime expiresAt;

    /** Active and not yet lapsed. */
    public boolean isEffective() {
        return Boolean.TRUE.equals(isActive) && (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
    }

    @PrePersist
    protected void onCreate() {
        grantedAt = LocalDateTime.now();
    }
}
