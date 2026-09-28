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

/** One period's review of one attaché's accounts (S-04, V099); {@code countryId} null is the ministry's own staff. */
@Getter
@Setter
@Entity
@Table(name = "AccessReviews", schema = "UMSCORE")
public class UmsAccessReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "Period", nullable = false, length = 20)
    private String period;

    @Column(name = "CountryId")
    private Integer countryId;

    @Column(name = "Status", nullable = false, length = 20)
    private String status = com.dilaw.mohe.codes.AccessReviewStatus.OPEN;

    @Column(name = "OpenedAt", nullable = false)
    private LocalDateTime openedAt = LocalDateTime.now();

    @Column(name = "OpenedBy")
    private Long openedBy;

    @Column(name = "DueAt", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "ClosedAt")
    private LocalDateTime closedAt;

    @Column(name = "ClosedBy")
    private Long closedBy;
}
