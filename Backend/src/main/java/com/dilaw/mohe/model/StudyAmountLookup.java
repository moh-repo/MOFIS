package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "STUDY_AMOUNT_LOOKUP", schema = "MOHECORE")
public class StudyAmountLookup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "STUDY_AMOUNT_ID", nullable = false)
    private Integer id;

    @Column(name = "STUDENT_DEGREE_ID", nullable = false)
    private Integer studentDegreeId;

    @Column(name = "STUDENT_AGENCY_TYPE_ID", nullable = false)
    private Integer studentAgencyTypeId;

    @Column(name = "AMOUNT", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "CURRENCY_ID")
    private Integer currencyId;

    /**
     * The attaché this rule belongs to, or {@code null} for the shared default.
     *
     * <p>Same rule as {@link StudyYearsLookup#getStudyCountryInId()}: a null is
     * the amount that applies wherever an attaché has not set its own, and
     * {@code UQ_STUDY_AMOUNT_COUNTRY_DEGREE_AGENCY} permits exactly one of them
     * per (degree, agency type).
     */
    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;

    /**
     * Written by the database, never by us — see
     * {@link StudyYearsLookup#getMidate()} for why {@code insertable = false} is
     * required rather than cosmetic.
     */
    @ColumnDefault("getdate()")
    @Column(name = "MIDATE", insertable = false, updatable = false)
    private Instant midate;
}
