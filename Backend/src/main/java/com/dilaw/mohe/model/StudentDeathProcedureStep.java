package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_DEATH_PROCEDURE_STEP", schema = "MOHECORE")
public class StudentDeathProcedureStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "STEP_ID", nullable = false)
    private Integer stepId;

    @Column(name = "DEATH_CASE_ID", nullable = false)
    private Integer deathCaseId;

    @Column(name = "PROCEDURE_ID", nullable = false)
    private Integer procedureId;

    @ColumnDefault("0")
    @Column(name = "IS_COMPLETED", nullable = false)
    private Boolean isCompleted = false;

    @Column(name = "COMPLETED_DATE")
    private LocalDateTime completedDate;

    @Nationalized
    @Column(name = "COMPLETED_BY", length = 100)
    private String completedBy;

    @Nationalized
    @Column(name = "NOTES", length = 1000)
    private String notes;
}
