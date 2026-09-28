package com.dilaw.mohe.ums.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "UserSessions", schema = "UMSCORE")
public class UmsUserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "UserId", nullable = false)
    @JsonIgnoreProperties({ "passwordHash", "passwordSalt", "userGroups", "userRoles", "userCountries",
            "hibernateLazyInitializer", "handler" })
    private UmsUser user;

    @Column(name = "TokenHash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "IpAddress", length = 50)
    private String ipAddress;

    @Column(name = "UserAgent", length = 500)
    private String userAgent;

    @Column(name = "DeviceInfo", length = 250)
    private String deviceInfo;

    /** S-09: SHA-256 of the user agent at sign-in; null on rows issued before V100. */
    @Column(name = "UserAgentHash", length = 64)
    private String userAgentHash;

    /** S-09: how often the session was rebound to a new address and device. */
    @Column(name = "AnomalyCount")
    private Integer anomalyCount = 0;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "ExpiresAt", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "IsActive", nullable = false)
    private Boolean isActive = true;
}
