package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "STUDY_YEARS_LOOKUP", schema = "MOHECORE")
public class StudyYearsLookup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "STUDY_YEARS_ID", nullable = false)
    private Integer id;

    @Column(name = "STUDENT_DEGREE_ID", nullable = false)
    private Integer studentDegreeId;

    @Column(name = "STUDENT_MAJOR_ID", nullable = false)
    private Integer studentMajorId;

    @Column(name = "STUDY_YEARS", nullable = false)
    private Integer studyYears;

    /**
     * The attaché this rule belongs to, or {@code null} for the shared default.
     *
     * <p>A null here is a deliberate statement rather than missing data: it is
     * the number of years that applies wherever an attaché has not set its own.
     * {@code UQ_STUDY_YEARS_COUNTRY_DEGREE_MAJOR} allows exactly one such row per
     * (degree, major), because SQL Server treats nulls as equal inside a unique
     * constraint — so "there is one default" is enforced by the database.
     *
     * <p>Resolution prefers the country row and falls back to the default; see
     * {@code StudyYearsLookupRepository.resolve}.
     */
    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;

    /**
     * When the row was created. Written by the database, never by us.
     *
     * <p>{@code insertable = false} is load-bearing: the column is NOT NULL with a
     * {@code getdate()} default, and {@code @ColumnDefault} is only a hint for
     * schema generation — it does not stop Hibernate sending an explicit NULL,
     * which overrides the default and fails the constraint. Adding a row through
     * this screen has therefore always failed with
     * "Cannot insert the value NULL into column 'MIDATE'".
     *
     * <p>Same defect shape as the MOHE_QUARTER identity mapping recorded in the
     * August review, and the same on {@link StudyAmountLookup}.
     */
    @ColumnDefault("getdate()")
    @Column(name = "MIDATE", insertable = false, updatable = false)
    private Instant midate;
}
