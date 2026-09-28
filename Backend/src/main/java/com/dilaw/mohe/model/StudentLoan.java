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
@Table(name = "STUDENT_LOAN", schema = "MOHECORE")
public class StudentLoan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LOAN_ID", nullable = false)
    private Integer loanId;

    @Column(name = "STUDENT_ID", nullable = false)
    private Integer studentId;

    @Nationalized
    @Column(name = "STUDY_YEAR", length = 50)
    private String studyYear;

    @Column(name = "MOHE_QUARTER_ID")
    private Integer moheQuarterId;

    @ColumnDefault("0")
    @Column(name = "LOAN_AMOUNT")
    private Integer loanAmount;

    @ColumnDefault("0")
    @Column(name = "CURRENCY_ID")
    private Integer currencyId;

    @ColumnDefault("0")
    @Column(name = "LOAN_STATUS")
    private Integer loanStatus;

    @Nationalized
    @Lob
    @Column(name = "REPAYMENT_NOTES")
    private String repaymentNotes;

    /**
     * Stamped by the service on every write, never by the body.
     *
     * <p>The column is {@code NOT NULL} with a {@code sysutcdatetime()} default,
     * but a default only applies to a column the INSERT leaves out — and
     * Hibernate names every mapped column. The dialogs sent {@code "midate":
     * null}, Jackson wrote that over the {@code Instant.now()} below, and the
     * insert carried a null into a column that refuses one.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "MIDATE", nullable = false)
    private Instant midate = Instant.now();

    /**
     * The country the row is filed under, taken from the student it is for.
     *
     * <p>A real FK ({@code FK_STUDENT_LOAN_STUDY_COUNTRY_IN}) that this entity
     * did not map at all, so every row the screen created would have left it
     * NULL. Read-only to Jackson: nothing on the screen names a country, and a
     * body must not be able to move a row out of its own.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;
}
