package com.dilaw.mohe.accounting.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/**
 * A segment of the account code.
 *
 * <p>Seeded for this ministry's flow: {@code ECONOMIC} (the fee type),
 * {@code ADMIN} (the cultural attaché that spends), {@code FUNDING_SOURCE}
 * (budget money or Wafar money), plus functional, project and governorate.
 *
 * <p>{@code ADMIN} and {@code FUNDING_SOURCE} carry the weight: together they
 * are what lets a report answer "how much of Cairo's spend this quarter came
 * from Wafar rather than from the quarterly allocation" — a question the
 * pre-existing schema could not express at all.
 */
@Getter
@Setter
@Entity
@Table(name = "COA_SEGMENT", schema = "FINCORE")
public class CoaSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SEGMENT_ID", nullable = false)
    private Integer id;

    @Column(name = "SEGMENT_CODE", nullable = false, length = 30)
    private String segmentCode;

    @Nationalized
    @Column(name = "NAME_AR", nullable = false, length = 150)
    private String nameAr;

    @Nationalized
    @Column(name = "NAME_EN", nullable = false, length = 150)
    private String nameEn;

    /** Position of this segment within the composite account code. */
    @Column(name = "ORDINAL", nullable = false)
    private Short ordinal;

    @Column(name = "IS_REQUIRED", nullable = false)
    private Boolean required = false;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean active = true;
}
