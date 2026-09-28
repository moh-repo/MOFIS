package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "StudentPortalSession", schema = "MOHECORE")
public class StudentPortalSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id", nullable = false)
    private Integer id;

    @Column(name = "StudentId", nullable = false)
    private Integer studentId;

    @Column(name = "Token", nullable = false, unique = true, length = 255)
    private String token;

    @Column(name = "ExpiresAt", nullable = false)
    private Instant expiresAt;

    @Column(name = "CreatedAt", nullable = false)
    private Instant createdAt = Instant.now();
}
