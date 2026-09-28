package com.dilaw.mohe.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_GENDER", schema = "MOHECORE")
public class StudentGender {
    /**
     * Not an IDENTITY column in SQL Server - the baseline schema declares it
     * {@code int NOT NULL DEFAULT ((0))} - so it must be assigned before insert;
     * the service sets it from {@code repository.nextId()}. Same situation as
     * {@link StudyFinanceBank}.
     */
    @Id
    @ColumnDefault("0")
    @Column(name = "STUDENT_GENDER_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDENT_GENDER_NAME")
    private String studentGenderName;

    @Column(name = "STUDENT_GENDER_NAME_EN")
    private String studentGenderNameEn;

    /**
     * Stable key for this gender (V075): MALE, FEMALE.
     *
     * <p>Eleven report views split their male and female columns on this, so a
     * row without one is counted in neither. It was unmapped until 1 Sep 2026,
     * which is why the Lookup Tables screen could neither show nor set it and
     * every row added there was written with CODE NULL — one was, and
     * {@code ReportViewsReadWhatIsRecordedTest} has failed on it since. V082
     * derives a code for the rows that lacked one and makes the column
     * {@code NOT NULL}; {@code @NotBlank} is the same rule one layer up, so the
     * screen refuses before the database has to.
     */
    @NotBlank
    @Size(max = 50)
    @Column(name = "CODE", nullable = false, length = 50)
    private String code;

    /** No field initialiser — see {@link StudyFinanceBank#getIsActive()}; a new row is active unless stated (L1). */
    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @PrePersist
    void defaultActive() {
        if (isActive == null) isActive = true;
    }

    @ColumnDefault("getdate()")
    @Column(name = "STUDENT_GENDER_MIDATE")
    private Instant studentGenderMidate;

}