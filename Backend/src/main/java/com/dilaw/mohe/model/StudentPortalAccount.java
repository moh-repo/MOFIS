package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "StudentPortalAccount", schema = "MOHECORE")
public class StudentPortalAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id", nullable = false)
    private Integer id;

    @Column(name = "StudentId", nullable = false)
    private Integer studentId;

    @Column(name = "LoginKey", nullable = false, unique = true, length = 128)
    private String loginKey;

    @Column(name = "PasswordHash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "LastLoginAt")
    private Instant lastLoginAt;

    @Column(name = "IsActive", nullable = false)
    private Boolean isActive = true;

    @Column(name = "Email", length = 255)
    private String email;

    @Column(name = "CreatedAt", nullable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StudentId", insertable = false, updatable = false)
    private StudentDatum student;
}
