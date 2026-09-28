package com.dilaw.mohe.approval.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A staff account named as an approver of one level, optionally for one country.
 *
 * <p>Security plan N-03 (V093). A level may say "the attaché" (a position,
 * resolved per country) or name people outright; when it names people, only
 * they may approve the level, and a row with a country applies to requests of
 * that country alone. Both come from {@code UMSCORE.Users}: the config screen
 * picks from live staff rather than typing names.
 */
@Getter
@Setter
@Entity
@Table(name = "ApprovalLevelApprovers", schema = "ALERTCORE",
        uniqueConstraints = @UniqueConstraint(columnNames = {"LevelId", "UserId", "CountryId"}))
public class ApprovalLevelApprover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LevelId", nullable = false)
    private ApprovalLevel level;

    @Column(name = "UserId", nullable = false)
    private Long userId;

    /** Null: approves this level for every country in their scope. */
    @Column(name = "CountryId")
    private Integer countryId;

    @Column(name = "CreatedAt", nullable = false)
    private Instant createdAt = Instant.now();

    /** For display; filled by {@code ApprovalConfigService}. */
    @Transient
    private String userName;

    @Transient
    private String fullName;
}
