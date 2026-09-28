package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_DEGREE", schema = "MOHECORE")
public class StudentDegree {
    /**
     * Not an IDENTITY column in SQL Server - the baseline schema declares it
     * {@code int NOT NULL DEFAULT ((0))} - so it must be assigned before insert;
     * the service sets it from {@code repository.nextId()}. Same situation as
     * {@link StudyFinanceBank}.
     */
    @Id
    @ColumnDefault("0")
    @Column(name = "STUDENT_DEGREE_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDENT_DEGREE_NAME")
    private String studentDegreeName;

    @Column(name = "STUDENT_DEGREE_NAME_EN")
    private String studentDegreeNameEn;

    /** No field initialiser — see {@link StudyFinanceBank#getIsActive()}; a new row is active unless stated (L1). */
    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @PrePersist
    void defaultActive() {
        if (isActive == null) isActive = true;
    }

    @ColumnDefault("0")
    @Column(name = "STUDENT_DEGREE_ORDER_NO")
    private Integer studentDegreeOrderNo;

    @ColumnDefault("getdate()")
    @Column(name = "STUDENT_DEGREE_INSERTED_DATE")
    private Instant studentDegreeInsertedDate;

}