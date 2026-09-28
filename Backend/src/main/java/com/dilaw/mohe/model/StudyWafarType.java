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
@Table(name = "STUDY_WAFAR_TYPE", schema = "MOHECORE")
public class StudyWafarType {

    /**
     * Not an IDENTITY column in SQL Server - the baseline schema declares it
     * {@code int NOT NULL DEFAULT ((0))} - so it must be assigned before insert;
     * the service sets it from {@code repository.nextId()}. Same situation as
     * {@link StudyFinanceBank}.
     */
    @Id
    @Column(name = "WAFAR_TYPE_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "WAFAR_NAME")
    private String wafarName;

    @Column(name = "WAFAR_NAME_EN")
    private String wafarNameEn;

    /** No field initialiser — see {@link StudyFinanceBank#getIsActive()}. */
    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @Nationalized
    @Lob
    @Column(name = "WAFAR_NOTE_1")
    private String wafarNote1;

    @Column(name = "SWAFAR_MIDATE")
    private Instant swafarMidate;
}
