package com.dilaw.mohe.studentportal.model;

import com.dilaw.mohe.codes.PortalAccountStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A portal account.
 *
 * <p>The password hash and the encrypted TOTP secret never leave the server.
 * {@code StudentUserAdminController} returns this entity as the JSON of the
 * portal-users administration page, and until the live test of 24 Sep 2026
 * (H3) that JSON carried both columns to every browser holding the page. They
 * are {@link JsonIgnore}d here, on the entity, rather than hidden by a DTO in
 * one controller: any future endpoint that serialises a user gets the same
 * protection. Nothing deserialises the entity from JSON, so the ignore has no
 * write side to break ({@code StudentUserSerializationTest}).
 */
@Getter
@Setter
@Entity
@Table(name = "student_users", schema = "STUDENTCORE")
public class StudentUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "student_user_id")
    private Integer studentUserId;

    @Column(name = "student_id", nullable = false, unique = true)
    private Integer studentId;

    @Column(name = "username", nullable = false, unique = true, length = 255)
    private String username;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "status", nullable = false, length = 20)
    private String status = PortalAccountStatus.ACTIVE;

    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    // S-11: e-mail verification on first sign-in, and the optional second factor.
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @JsonIgnore
    @Column(name = "totp_secret_enc", length = 512)
    private String totpSecretEnc;

    @Column(name = "totp_enabled", nullable = false)
    private Boolean totpEnabled = false;

    @Column(name = "totp_enrolled_at")
    private Instant totpEnrolledAt;

    @Column(name = "totp_failed_attempts", nullable = false)
    private Integer totpFailedAttempts = 0;

    @Column(name = "totp_locked_until")
    private Instant totpLockedUntil;
}
