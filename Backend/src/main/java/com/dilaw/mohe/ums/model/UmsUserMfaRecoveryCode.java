package com.dilaw.mohe.ums.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** A one-time recovery code (O-16), kept as a keyed hash; a used one keeps its {@code usedAt}. */
@Getter
@Setter
@Entity
@Table(name = "UserMfaRecoveryCodes", schema = "UMSCORE")
public class UmsUserMfaRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "UserId", nullable = false)
    private Long userId;

    @Column(name = "CodeHash", nullable = false, length = 128)
    private String codeHash;

    @Column(name = "UsedAt")
    private LocalDateTime usedAt;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
