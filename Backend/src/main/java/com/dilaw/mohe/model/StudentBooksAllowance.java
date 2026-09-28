package com.dilaw.mohe.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_BOOKS_ALLOWANCE", schema = "MOHECORE")
public class StudentBooksAllowance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BOOKS_ALLOW_ID", nullable = false)
    private Integer booksAllowId;

    @Column(name = "STUDENT_ID", nullable = false)
    private Integer studentId;

    @Nationalized
    @Column(name = "STUDY_YEAR", length = 50)
    private String studyYear;

    @Column(name = "MOHE_QUARTER_ID")
    private Integer moheQuarterId;

    @ColumnDefault("0")
    @Column(name = "ALLOWANCE_AMOUNT")
    private Integer allowanceAmount;

    @ColumnDefault("0")
    @Column(name = "PAY_STATUS")
    private Integer payStatus;

    @Column(name = "RECEIPT_DATE")
    private Instant receiptDate;

    @Nationalized
    @Lob
    @Column(name = "NOTES")
    private String notes;

    /**
     * When the row was last written. {@code datetime2 NOT NULL}.
     *
     * <p>Read-only to Jackson, and not by preference: both dialogs put a literal
     * {@code "midate": null} in the body, which overwrote the default below, and
     * Hibernate then named the column in the INSERT — so the database default
     * {@code sysutcdatetime()} never got the chance to fire. The server stamps
     * this; a request cannot.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "MIDATE", nullable = false)
    private Instant midate = Instant.now();

    /**
     * The country the row belongs to, copied from the student when it is created.
     *
     * <p>A real FK to {@code STUDY_COUNTRY_IN} that the entity did not map, so
     * every row this screen wrote would have left it NULL — where all fifteen
     * rows that were there before hold a country. Read-only to Jackson for the
     * same reason as above: it is derived from the student, not typed.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;
}
