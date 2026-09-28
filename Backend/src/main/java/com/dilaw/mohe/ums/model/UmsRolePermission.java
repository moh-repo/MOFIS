package com.dilaw.mohe.ums.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "RolePermissions", schema = "UMSCORE")
public class UmsRolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RoleId", nullable = false)
    private UmsRole role;

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

    @PrePersist
    protected void onCreate() {
        grantedAt = LocalDateTime.now();
    }
}
