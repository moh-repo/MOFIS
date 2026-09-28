package com.dilaw.mohe.ums.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One account's second factor (security plan O-16, V097). The secret is stored encrypted. */
@Getter
@Setter
@Entity
@Table(name = "UserMfa", schema = "UMSCORE")
public class UmsUserMfa {

    @Id
    @Column(name = "UserId")
    private Long userId;

    @Column(name = "SecretEncrypted", nullable = false, length = 512)
    private String secretEncrypted;

    @Column(name = "Enabled", nullable = false)
    private boolean enabled;

    @Column(name = "EnrolledAt")
    private LocalDateTime enrolledAt;

    /** The last TOTP step a code was accepted for; a code for that step or earlier is refused (replay). */
    @Column(name = "LastUsedStep")
    private Long lastUsedStep;

    @Column(name = "FailedAttempts", nullable = false)
    private int failedAttempts;

    @Column(name = "LockedUntil")
    private LocalDateTime lockedUntil;

    @Column(name = "ResetBy")
    private Long resetBy;

    @Column(name = "ResetAt")
    private LocalDateTime resetAt;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;
}
