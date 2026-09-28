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

/**
 * A staff account's previous password hashes, so a "new" password cannot be one
 * of the last few (security plan O-25, 6 Sep 2026; table from V087).
 *
 * <p>Only hashes are kept — the same BCrypt hashes that were on the account —
 * and only as many as the history rule needs; older rows are pruned on write.
 */
@Getter
@Setter
@Entity
@Table(name = "UserPasswordHistory", schema = "UMSCORE")
public class UmsUserPasswordHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "UserId", nullable = false)
    private Long userId;

    @Column(name = "PasswordHash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "ChangedAt", nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();
}
