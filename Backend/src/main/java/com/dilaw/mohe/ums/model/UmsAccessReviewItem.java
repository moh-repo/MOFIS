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

/** One account in a review: what it held when the review opened, and the attestation (S-04). */
@Getter
@Setter
@Entity
@Table(name = "AccessReviewItems", schema = "UMSCORE")
public class UmsAccessReviewItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "ReviewId", nullable = false)
    private Long reviewId;

    @Column(name = "UserId", nullable = false)
    private Long userId;

    @Column(name = "Snapshot", nullable = false)
    private String snapshot;

    /** KEEP, CHANGE or REMOVE; null until attested. */
    @Column(name = "Decision", length = 10)
    private String decision;

    @Column(name = "DecidedBy")
    private Long decidedBy;

    @Column(name = "DecidedAt")
    private LocalDateTime decidedAt;

    @Column(name = "Note", length = 500)
    private String note;

    @Column(name = "OverdueFlaggedAt")
    private LocalDateTime overdueFlaggedAt;
}
