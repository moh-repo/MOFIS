package com.dilaw.mohe.model;

import com.dilaw.mohe.codes.DeathCaseStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_DEATH_CASE", schema = "MOHECORE")
@Where(clause = "IS_DELETED = 0 OR IS_DELETED IS NULL")
public class StudentDeathCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DEATH_CASE_ID", nullable = false)
    private Integer deathCaseId;

    @Column(name = "STUDENT_ID", nullable = false)
    private Integer studentId;

    @Column(name = "DATE_OF_DEATH", nullable = false)
    private LocalDate dateOfDeath;

    @Column(name = "LEVEL_AT_DEATH_ID")
    private Integer levelAtDeathId;

    @Column(name = "STUDY_YEAR_AT_DEATH")
    private Integer studyYearAtDeath;

    @Nationalized
    @Column(name = "PLACE_OF_DEATH", length = 300)
    private String placeOfDeath;

    @Nationalized
    @Column(name = "CAUSE", length = 500)
    private String cause;

    @Column(name = "REPORTED_DATE")
    private LocalDateTime reportedDate;

    @Nationalized
    @Column(name = "REPORTED_BY", length = 100)
    private String reportedBy;

    @Nationalized
    @ColumnDefault("N'OPEN'")
    @Column(name = "CASE_STATUS", nullable = false, length = 20)
    private String caseStatus = DeathCaseStatus.OPEN;

    @Nationalized
    @Column(name = "NOTES", length = 2000)
    private String notes;

    @Column(name = "CREATED_DATE")
    private LocalDateTime createdDate;

    @Nationalized
    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @Column(name = "MODIFIED_DATE")
    private LocalDateTime modifiedDate;

    @Nationalized
    @Column(name = "MODIFIED_BY", length = 100)
    private String modifiedBy;

    @ColumnDefault("0")
    @Column(name = "IS_DELETED", nullable = false)
    private Boolean isDeleted = false;
}
