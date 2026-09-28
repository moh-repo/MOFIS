package com.dilaw.mohe.ums.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "UserActions", schema = "UMSCORE")
public class UmsAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "UserId")
    private Long userId;

    /** Not stored in DB (UserActions has no username column). */
    @Transient
    private String userName;

    @Column(name = "Action", nullable = false, length = 100)
    private String action; // LOGIN, LOGOUT, PERMISSION_CHANGE, etc.

    @Column(name = "ResourceType", length = 100)
    private String resourceType;

    @Column(name = "ResourceId")
    private Long resourceId;

    @Column(name = "OldValue", columnDefinition = "NVARCHAR(MAX)")
    private String oldValue;

    @Column(name = "NewValue", columnDefinition = "NVARCHAR(MAX)")
    private String newValue;

    @Column(name = "IpAddress", length = 50)
    private String ipAddress;

    @Column(name = "UserAgent", length = 500)
    private String userAgent;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
